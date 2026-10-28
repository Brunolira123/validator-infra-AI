package br.com.vrinteriorpaulista.validator_infra.prospeccao.google;

import br.com.vrinteriorpaulista.validator_infra.exception.LimiteExcedidoException;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Places API (New). A chave só existe aqui no backend; o navegador nunca fala com o Google.
 * O FieldMask fica restrito a campos da faixa Pro: rating, avaliações, telefone e site jogariam a chamada
 * para a faixa Enterprise (cota grátis 5x menor).
 */
@Component
@Slf4j
public class GooglePlacesClient {

    static final String CAMPOS_LUGAR = "id,displayName,formattedAddress,location,photos,businessStatus,googleMapsUri";
    static final String CAMPOS_BUSCA = prefixar(CAMPOS_LUGAR) + ",nextPageToken";

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String MENSAGEM_INDISPONIVEL = "Busca do Google indisponível. Tente novamente.";
    static final String MENSAGEM_NAO_CONFIGURADA = "Busca de prospects não configurada";
    static final String MENSAGEM_COTA_GOOGLE = "Google limitou as buscas, tente em instantes";

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public GooglePlacesClient(RestClient.Builder builder,
                              @Value("${app.google.places.base-url:https://places.googleapis.com/v1}") String baseUrl,
                              @Value("${app.google.places.api-key:}") String apiKey) {
        // A foto responde com redirect para o googleusercontent; o HttpClient do JDK não segue por padrão
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    /** Para testes com MockRestServiceServer. */
    GooglePlacesClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public PaginaGoogle buscarTexto(String query, String pageToken) {
        exigirChave();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("textQuery", query);
        corpo.put("languageCode", "pt-BR");
        corpo.put("regionCode", "BR");
        corpo.put("pageSize", 20);
        if (pageToken != null && !pageToken.isBlank()) {
            corpo.put("pageToken", pageToken);
        }

        PaginaGoogle pagina = executar(() -> restClient.post()
                .uri("/places:searchText")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", CAMPOS_BUSCA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(PaginaGoogle.class));
        return pagina != null ? pagina : new PaginaGoogle(null, null);
    }

    /** Vazio quando o lugar não existe mais no Google (placeId antigo ou removido). */
    public Optional<LugarGoogle> detalhes(String placeId) {
        exigirChave();
        try {
            return Optional.ofNullable(executar(() -> restClient.get()
                    .uri("/places/{id}?languageCode=pt-BR&regionCode=BR", placeId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", CAMPOS_LUGAR)
                    .retrieve()
                    .body(LugarGoogle.class)));
        } catch (EntityNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * @param nomeFoto "places/{placeId}/photos/{ref}", como vem em LugarGoogle.Foto.name
     */
    public FotoGoogle baixarFoto(String nomeFoto, int larguraMaxima) {
        exigirChave();
        ResponseEntity<byte[]> resposta;
        try {
            resposta = executar(() -> restClient.get()
                    .uri(b -> b.path("/" + nomeFoto + "/media")
                            .queryParam("maxWidthPx", larguraMaxima)
                            .queryParam("skipHttpRedirect", false)
                            .build())
                    .header("X-Goog-Api-Key", apiKey)
                    .retrieve()
                    .toEntity(byte[].class));
        } catch (IllegalArgumentException e) {
            // 400 do Google: referência de foto expirada ou malformada
            throw new EntityNotFoundException("Foto não encontrada");
        }
        if (resposta.getBody() == null || resposta.getBody().length == 0) {
            throw new EntityNotFoundException("Foto não encontrada");
        }
        MediaType tipo = resposta.getHeaders().getContentType();
        return new FotoGoogle(resposta.getBody(), tipo != null ? tipo.toString() : MediaType.IMAGE_JPEG_VALUE);
    }

    private void exigirChave() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ServicoIndisponivelException(MENSAGEM_NAO_CONFIGURADA, null);
        }
    }

    private <T> T executar(Chamada<T> chamada) {
        try {
            return chamada.executar();
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("Lugar não encontrado no Google");
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Google Places recusou o pedido (400): {}", e.getResponseBodyAsString());
            throw new IllegalArgumentException("Pedido recusado pelo Google", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // Pode ser a cota por minuto do Google Cloud, não só a diária
            throw new LimiteExcedidoException(MENSAGEM_COTA_GOOGLE);
        } catch (HttpClientErrorException e) {
            // 401/403: chave inválida, API não ativada ou restrição da chave. Não é culpa do usuário.
            log.error("Google Places recusou a chamada ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServicoIndisponivelException(MENSAGEM_INDISPONIVEL, e);
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException(MENSAGEM_INDISPONIVEL, e);
        }
    }

    private static String prefixar(String campos) {
        return "places." + campos.replace(",", ",places.");
    }

    @FunctionalInterface
    private interface Chamada<T> {
        T executar();
    }
}
