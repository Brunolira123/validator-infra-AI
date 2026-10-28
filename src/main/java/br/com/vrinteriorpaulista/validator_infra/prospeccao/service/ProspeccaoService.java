package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.exception.LimiteExcedidoException;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.LugarEncontradoResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.PaginaBuscaProspectResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.FotoGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.GooglePlacesClient;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.LugarGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.PaginaGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.ProspectRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Busca de comércios de alimentos no Google: uma query por categoria, resultados juntos e sem duplicados.
 * Tudo que vem do Google fica só em cache de memória por 24h (não pagar a mesma busca duas vezes no dia).
 */
@Service
@Slf4j
public class ProspeccaoService {

    private static final Duration VALIDADE_CACHE = Duration.ofHours(24);
    private static final long LIMITE_CACHE_FOTOS_BYTES = 50L * 1024 * 1024;
    /** Só referências de foto do próprio Google: impede usar o endpoint como proxy aberto. */
    private static final Pattern NOME_FOTO = Pattern.compile("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_-]+");
    private static final Pattern UF = Pattern.compile("[A-Z]{2}");
    static final int LARGURA_MINIMA = 100;
    static final int LARGURA_MAXIMA = 800;

    private final GooglePlacesClient google;
    private final CotaDiariaGoogle cota;
    private final ObjectMapper objectMapper;
    private final ProspectRepository prospectRepo;

    private final Cache<ChaveBusca, PaginaGoogle> cacheBusca = Caffeine.newBuilder()
            .expireAfterWrite(VALIDADE_CACHE)
            .maximumSize(2_000)
            .build();
    /** Optional vazio também fica em cache: lugar que sumiu do Google não é consultado de novo no mesmo dia. */
    private final Cache<String, Optional<LugarGoogle>> cacheDetalhes = Caffeine.newBuilder()
            .expireAfterWrite(VALIDADE_CACHE)
            .maximumSize(5_000)
            .build();
    private final Cache<ChaveFoto, FotoGoogle> cacheFotos = Caffeine.newBuilder()
            .expireAfterWrite(VALIDADE_CACHE)
            .maximumWeight(LIMITE_CACHE_FOTOS_BYTES)
            .weigher((ChaveFoto chave, FotoGoogle foto) -> foto.bytes().length)
            .build();

    /** Categorias e detalhes são consultados em paralelo: em série, 11 ou 20 chamadas ao Google levam vários segundos. */
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "google-places");
        t.setDaemon(true);
        return t;
    });

    public ProspeccaoService(GooglePlacesClient google, CotaDiariaGoogle cota, ObjectMapper objectMapper,
                             ProspectRepository prospectRepo) {
        this.google = google;
        this.cota = cota;
        this.objectMapper = objectMapper;
        this.prospectRepo = prospectRepo;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    /**
     * @param categorias vazio ou null = todas
     * @param pagina     token de "carregar mais" devolvido em proximaPagina; quando vem, manda nas categorias
     */
    public PaginaBuscaProspectResponse buscar(String cidade, String uf, List<CategoriaProspeccao> categorias, String pagina) {
        String cidadeLimpa = cidade == null ? "" : cidade.trim();
        String ufLimpa = uf == null ? "" : uf.trim().toUpperCase(Locale.ROOT);
        if (cidadeLimpa.isEmpty()) {
            throw new IllegalArgumentException("Informe a cidade");
        }
        if (!UF.matcher(ufLimpa).matches()) {
            throw new IllegalArgumentException("UF inválida");
        }

        Map<CategoriaProspeccao, String> tokens = pagina != null && !pagina.isBlank()
                ? decodificarPagina(pagina)
                : primeiraPagina(categorias);

        List<CompletableFuture<Resultado>> chamadas = tokens.entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(
                        () -> new Resultado(e.getKey(), buscarCategoria(e.getKey().query(cidadeLimpa, ufLimpa), e.getValue())),
                        executor))
                .toList();

        Map<String, LugarGoogle> lugares = new LinkedHashMap<>();
        Map<String, EnumSet<CategoriaProspeccao>> categoriasPorLugar = new HashMap<>();
        Map<CategoriaProspeccao, String> proximas = new EnumMap<>(CategoriaProspeccao.class);
        for (CompletableFuture<Resultado> chamada : chamadas) {
            Resultado resultado = aguardar(chamada);
            for (LugarGoogle lugar : resultado.pagina().lugares()) {
                if (lugar.id() == null || LugarGoogle.FECHADO_DEFINITIVAMENTE.equals(lugar.businessStatus())) {
                    continue;
                }
                lugares.putIfAbsent(lugar.id(), lugar);
                categoriasPorLugar.computeIfAbsent(lugar.id(), id -> EnumSet.noneOf(CategoriaProspeccao.class))
                        .add(resultado.categoria());
            }
            String proxima = resultado.pagina().nextPageToken();
            if (proxima != null && !proxima.isBlank()) {
                proximas.put(resultado.categoria(), proxima);
            }
        }

        // Uma query só para marcar os lugares que já são prospect
        Map<String, Prospect> salvos = lugares.isEmpty() ? Map.of() : prospectRepo.findByPlaceIdIn(lugares.keySet()).stream()
                .collect(Collectors.toMap(Prospect::getPlaceId, Function.identity()));
        List<LugarEncontradoResponse> resposta = lugares.values().stream()
                .map(l -> {
                    LugarEncontradoResponse lugar = LugarEncontradoResponse.from(l, List.copyOf(categoriasPorLugar.get(l.id())));
                    Prospect prospect = salvos.get(l.id());
                    return prospect != null ? lugar.comProspect(prospect) : lugar;
                })
                .toList();
        return new PaginaBuscaProspectResponse(resposta, proximas.isEmpty() ? null : codificarPagina(proximas));
    }

    /**
     * Dados do Google dos prospects de uma página da lista (chamar só para a página exibida, nunca para todos).
     * Prospect sem placeId, lugar que sumiu do Google, Google fora do ar ou limite diário de detalhes estourado:
     * fica fora do mapa, e a lista mostra o item só com os dados nossos.
     *
     * @return por id do prospect
     */
    public Map<Long, LugarEncontradoResponse> lugaresDosProspects(List<Prospect> prospects) {
        // Lê a entidade aqui, na thread da requisição; as threads do executor só falam com o Google
        record Pedido(Long id, String placeId, List<CategoriaProspeccao> categorias) {}
        List<Pedido> pedidos = prospects.stream()
                .filter(p -> p.getPlaceId() != null)
                .map(p -> new Pedido(p.getId(), p.getPlaceId(),
                        p.getCategoria() != null ? List.of(p.getCategoria()) : List.of()))
                .toList();

        Map<Long, CompletableFuture<Optional<LugarGoogle>>> chamadas = new LinkedHashMap<>();
        pedidos.forEach(p -> chamadas.put(p.id(), CompletableFuture.supplyAsync(() -> detalhes(p.placeId()), executor)));

        Map<Long, LugarEncontradoResponse> lugares = new HashMap<>();
        for (Pedido pedido : pedidos) {
            try {
                aguardar(chamadas.get(pedido.id()))
                        .ifPresent(l -> lugares.put(pedido.id(), LugarEncontradoResponse.from(l, pedido.categorias())));
            } catch (LimiteExcedidoException | ServicoIndisponivelException e) {
                log.warn("Detalhes do prospect {} indisponíveis: {}", pedido.id(), e.getMessage());
            } catch (RuntimeException e) {
                // Qualquer outra falha do Google também não pode derrubar a lista
                log.error("Erro ao buscar detalhes do prospect {}", pedido.id(), e);
            }
        }
        return lugares;
    }

    /** Place Details com cache de 24h por placeId; só cache miss conta na cota diária de detalhes. */
    Optional<LugarGoogle> detalhes(String placeId) {
        return cacheDetalhes.get(placeId, id -> {
            cota.consumirDetalhes();
            return google.detalhes(id);
        });
    }

    /** Proxy da foto (a chave do Google não pode ir para o navegador). Nunca grava em disco. */
    public FotoGoogle foto(String nome, Integer largura) {
        if (nome == null || !NOME_FOTO.matcher(nome).matches()) {
            throw new IllegalArgumentException("Foto inválida");
        }
        int w = largura == null ? LugarEncontradoResponse.LARGURA_FOTO
                : Math.max(LARGURA_MINIMA, Math.min(LARGURA_MAXIMA, largura));
        ChaveFoto chave = new ChaveFoto(nome, w);

        FotoGoogle emCache = cacheFotos.getIfPresent(chave);
        if (emCache != null) {
            return emCache;
        }
        if (!cota.tentarConsumirFoto()) {
            throw new EntityNotFoundException("Limite diário de fotos atingido");
        }
        FotoGoogle foto = google.baixarFoto(nome, w);
        cacheFotos.put(chave, foto);
        return foto;
    }

    private PaginaGoogle buscarCategoria(String query, String token) {
        return cacheBusca.get(new ChaveBusca(query, token), chave -> {
            cota.consumirBusca();
            return google.buscarTexto(chave.query(), chave.token());
        });
    }

    private static Map<CategoriaProspeccao, String> primeiraPagina(List<CategoriaProspeccao> categorias) {
        Collection<CategoriaProspeccao> escolhidas = categorias == null || categorias.isEmpty()
                ? List.of(CategoriaProspeccao.values())
                : categorias;
        Map<CategoriaProspeccao, String> tokens = new EnumMap<>(CategoriaProspeccao.class);
        escolhidas.forEach(c -> tokens.put(c, null));
        return tokens;
    }

    /** Token composto: {categoria: nextPageToken do Google} em JSON, base64url. */
    private String codificarPagina(Map<CategoriaProspeccao, String> proximas) {
        byte[] json = objectMapper.writeValueAsBytes(proximas);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    private Map<CategoriaProspeccao, String> decodificarPagina(String pagina) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(pagina), StandardCharsets.UTF_8);
            Map<CategoriaProspeccao, String> tokens = new EnumMap<>(CategoriaProspeccao.class);
            tokens.putAll(objectMapper.readValue(json, new TypeReference<Map<CategoriaProspeccao, String>>() {}));
            if (tokens.isEmpty() || tokens.values().stream().anyMatch(t -> t == null || t.isBlank())) {
                throw new IllegalArgumentException();
            }
            return tokens;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Página inválida. Faça a busca de novo.");
        }
    }

    private static <T> T aguardar(CompletableFuture<T> chamada) {
        try {
            return chamada.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw e;
        }
    }

    private record ChaveBusca(String query, String token) {}

    private record ChaveFoto(String nome, int largura) {}

    private record Resultado(CategoriaProspeccao categoria, PaginaGoogle pagina) {}
}
