package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.ClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.request.LevantamentoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.CnpjResponseDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.dto.response.PerfilTributarioDTO;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.*;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.*;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.ProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.VisitaProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.security.UsuarioAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.ClienteService;
import br.com.vrinteriorpaulista.validator_infra.service.LevantamentoService;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import br.com.vrinteriorpaulista.validator_infra.util.CnpjUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Funil do prospect de campo: salvar da busca, registrar visita, fechar (vira Cliente) ou perder, LTO (Levantamento
 * do cliente fechado) e descartar.
 * COMERCIAL (e demais perfis) só enxerga os prospects que salvou; ADMIN e DIRETORIA veem todos.
 */
@Service
@Slf4j
public class ProspectService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    /** Contrato fechado: não volta atrás (nem visita nem descarte). */
    private static final Set<StatusProspect> FECHADOS = Set.of(StatusProspect.FECHADO, StatusProspect.LTO);

    private final ProspectRepository repo;
    private final VisitaProspectRepository visitaRepo;
    private final ClienteRepository clienteRepo;
    private final ClienteService clienteService;
    private final LevantamentoService levantamentoService;
    private final ProspeccaoService prospeccao;
    private final UsuarioLogadoService usuarioLogado;
    private final TransactionTemplate tx;

    public ProspectService(ProspectRepository repo,
                           VisitaProspectRepository visitaRepo,
                           ClienteRepository clienteRepo,
                           ClienteService clienteService,
                           LevantamentoService levantamentoService,
                           ProspeccaoService prospeccao,
                           UsuarioLogadoService usuarioLogado,
                           TransactionTemplate tx) {
        this.repo = repo;
        this.visitaRepo = visitaRepo;
        this.clienteRepo = clienteRepo;
        this.clienteService = clienteService;
        this.levantamentoService = levantamentoService;
        this.prospeccao = prospeccao;
        this.usuarioLogado = usuarioLogado;
        this.tx = tx;
    }

    /** Resultado do salvar: o front recebe 201 quando criou e 200 quando o lugar já era prospect. */
    public record Salvo(ProspectResponse prospect, boolean criado) {}

    /**
     * Busca no Google e marca os lugares que já são prospect, com uma query só. Prospect de outro comercial
     * (para quem não é ADMIN/DIRETORIA) vem só como deOutroComercial, sem id, status nem temperatura;
     * se ele descartou ou perdeu, o lugar vem livre (qualquer um pode salvar e assumir).
     */
    public PaginaBuscaProspectResponse buscar(String cidade, String uf, List<CategoriaProspeccao> categorias, String pagina) {
        PaginaBuscaProspectResponse resultado = prospeccao.buscar(cidade, uf, categorias, pagina);
        if (resultado.lugares().isEmpty()) {
            return resultado;
        }
        List<String> placeIds = resultado.lugares().stream().map(LugarEncontradoResponse::placeId).toList();
        Map<String, Prospect> salvos = repo.findByPlaceIdIn(placeIds).stream()
                .collect(Collectors.toMap(Prospect::getPlaceId, Function.identity()));

        List<LugarEncontradoResponse> lugares = resultado.lugares().stream()
                .map(lugar -> {
                    Prospect prospect = salvos.get(lugar.placeId());
                    if (prospect == null) {
                        return lugar;
                    }
                    if (podeVer(prospect)) {
                        return lugar.comProspect(prospect);
                    }
                    return livre(prospect) ? lugar : lugar.comoDeOutroComercial();
                })
                .toList();
        return new PaginaBuscaProspectResponse(lugares, resultado.proximaPagina());
    }

    /**
     * Idempotente por placeId: se já existe e o usuário enxerga, devolve o existente.
     * Se é de outro comercial, 409 (não dá para dois trabalharem o mesmo lugar sem saber).
     * Descartado ou perdido volta ao funil como NOVO (201) com quem salvou de responsável, seja o mesmo usuário ou outro;
     * as visitas antigas ficam no histórico.
     * Dois salvando o mesmo lugar ao mesmo tempo: o segundo bate no unique e tenta de novo numa transação nova,
     * que já enxerga o prospect gravado pelo primeiro (mesmo usuário: 200 com o existente; outro comercial: 409).
     */
    public Salvo salvar(SalvarProspectRequest req) {
        try {
            return tx.execute(status -> salvarNaTransacao(req));
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() == null || !e.getMessage().contains(Prospect.UK_PLACE_ID)) {
                throw e;
            }
            log.info("Corrida ao salvar o placeId {}: repetindo numa transação nova", req.placeId());
            return tx.execute(status -> salvarNaTransacao(req));
        }
    }

    private Salvo salvarNaTransacao(SalvarProspectRequest req) {
        Prospect existente = repo.findByPlaceIdIncluindoExcluidos(req.placeId()).orElse(null);
        if (existente != null && existente.getExcluidoEm() == null && livre(existente)) {
            Usuario atual = usuarioLogado.usuario();
            if (!existente.getResponsavel().getId().equals(atual.getId())) {
                log.info("Prospect {} {} (placeId {}) transferido de {} para {}", existente.getStatus(), existente.getId(),
                        existente.getPlaceId(), existente.getResponsavel().getId(), atual.getId());
            } else {
                log.info("Prospect {} {} (placeId {}) reaberto", existente.getStatus(), existente.getId(),
                        existente.getPlaceId());
            }
            limparFunil(existente);
            existente.setStatus(StatusProspect.NOVO);
            existente.setResponsavel(atual);
            return new Salvo(ProspectResponse.from(repo.saveAndFlush(existente), null), true);
        }
        if (existente != null && existente.getExcluidoEm() == null) {
            if (!podeVer(existente)) {
                throw new IllegalStateException("Este lugar já é prospect de " + existente.getResponsavel().getNome());
            }
            return new Salvo(ProspectResponse.from(existente, null), false);
        }

        Prospect p = existente != null ? existente : new Prospect();
        if (existente != null) {
            log.info("Prospect {} (placeId {}) reativado ao salvar de novo", p.getId(), p.getPlaceId());
            p.setExcluidoEm(null);
            limparFunil(p);
            p.setCliente(null);
        }
        p.setOrigem(OrigemProspect.GOOGLE);
        p.setPlaceId(req.placeId());
        p.setCidade(req.cidade().trim());
        p.setUf(req.uf().trim().toUpperCase(Locale.ROOT));
        p.setCategoria(req.categoria());
        p.setStatus(StatusProspect.NOVO);
        p.setResponsavel(usuarioLogado.usuario());

        return new Salvo(ProspectResponse.from(repo.saveAndFlush(p), null), true);
    }

    /** Zera o resultado da última visita e da perda; as visitas (prospect_visita) não são apagadas. */
    private static void limparFunil(Prospect p) {
        p.setResultadoVisita(null);
        p.setTemperatura(null);
        p.setVisitadoEm(null);
        p.setObservacao(null);
        p.setFechadoEm(null);
        limparPerda(p);
    }

    private static void limparPerda(Prospect p) {
        p.setMotivoPerda(null);
        p.setObservacaoPerda(null);
    }

    /** Descartado ou perdido: outro comercial pode salvar e assumir. */
    private static boolean livre(Prospect p) {
        return p.getStatus() == StatusProspect.DESCARTADO || p.getStatus() == StatusProspect.PERDIDO;
    }

    /**
     * Lista paginada; sem filtro de status, os descartados ficam de fora.
     * O Google (Place Details) só é consultado para a página devolvida.
     */
    public PaginaProspectsResponse listar(String cidade, String uf, StatusProspect status,
                                         TemperaturaNegociacao temperatura, int pagina, int tamanho) {
        UsuarioAutenticado atual = usuarioLogado.atual();
        Specification<Prospect> filtro = (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (!veTodos(atual)) {
                condicoes.add(cb.equal(root.get("responsavel").get("id"), atual.id()));
            }
            if (cidade != null && !cidade.isBlank()) {
                condicoes.add(cb.equal(cb.lower(root.get("cidade")), cidade.trim().toLowerCase(Locale.ROOT)));
            }
            if (uf != null && !uf.isBlank()) {
                condicoes.add(cb.equal(root.get("uf"), uf.trim().toUpperCase(Locale.ROOT)));
            }
            if (status != null) {
                condicoes.add(cb.equal(root.get("status"), status));
            } else {
                condicoes.add(cb.notEqual(root.get("status"), StatusProspect.DESCARTADO));
            }
            if (temperatura != null) {
                condicoes.add(cb.equal(root.get("temperatura"), temperatura));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_PAGINA)),
                Sort.by(Sort.Order.desc("atualizadoEm"), Sort.Order.desc("id")));
        Page<Prospect> pageProspects = repo.findAll(filtro, pageRequest);

        Map<Long, LugarEncontradoResponse> lugares = prospeccao.lugaresDosProspects(pageProspects.getContent());
        List<ProspectResponse> itens = pageProspects.getContent().stream()
                .map(p -> ProspectResponse.from(p, lugares.get(p.getId())))
                .toList();
        return new PaginaProspectsResponse(itens, pageProspects.getNumber(), pageProspects.getTotalPages(),
                pageProspects.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProspectResponse detalhar(Long id) {
        Prospect p = buscarVisivel(id);
        return ProspectResponse.from(p, prospeccao.lugaresDosProspects(List.of(p)).get(p.getId()));
    }

    /** Pode haver várias visitas: cada uma grava histórico e a última define o prospect. PERDIDO reabre como VISITADO. */
    @Transactional
    public ProspectResponse registrarVisita(Long id, VisitaProspectRequest req) {
        if (req.resultado() == ResultadoVisita.ATENDEU && req.temperatura() == null) {
            throw new IllegalArgumentException("Informe a temperatura da negociação quando o cliente atendeu");
        }
        if (req.resultado() == ResultadoVisita.NAO_ATENDEU && req.temperatura() != null) {
            throw new IllegalArgumentException("Temperatura só vale quando o cliente atendeu");
        }
        Prospect p = buscarVisivel(id);
        if (FECHADOS.contains(p.getStatus())) {
            throw new IllegalStateException("Prospect com contrato fechado não recebe visita");
        }

        LocalDateTime agora = LocalDateTime.now();
        VisitaProspect visita = new VisitaProspect();
        visita.setProspect(p);
        visita.setUsuario(usuarioLogado.usuario());
        visita.setDataHora(agora);
        visita.setResultado(req.resultado());
        visita.setTemperatura(req.temperatura());
        visita.setObservacao(req.observacao());
        visita.setLatitude(req.latitude());
        visita.setLongitude(req.longitude());
        visitaRepo.save(visita);

        p.setStatus(StatusProspect.VISITADO);
        p.setResultadoVisita(req.resultado());
        p.setTemperatura(req.temperatura());
        p.setVisitadoEm(agora);
        p.setObservacao(req.observacao());
        limparPerda(p);
        return ProspectResponse.from(repo.save(p), null);
    }

    @Transactional
    public ProspectResponse descartar(Long id) {
        Prospect p = buscarVisivel(id);
        if (FECHADOS.contains(p.getStatus())) {
            throw new IllegalStateException("Prospect com contrato fechado não pode ser descartado");
        }
        p.setStatus(StatusProspect.DESCARTADO);
        return ProspectResponse.from(repo.save(p), null);
    }

    /**
     * Contrato fechado: prospect visitado e que atendeu vira Cliente (só com dados da Receita), reaproveitando o
     * Cliente se o CNPJ já estiver cadastrado (o ClienteService.criar reativa o arquivado). As regras de Cliente ficam
     * no service dele. A resposta traz o selo de perfil do CNPJ, que não bloqueia nada: com o cliente já cadastrado,
     * a Receita fora do ar só deixa o perfil null.
     */
    @Transactional
    public ProspectResponse fechar(Long id, FecharProspectRequest req) {
        Prospect p = buscarVisivel(id);
        if (p.getStatus() != StatusProspect.VISITADO || p.getResultadoVisita() != ResultadoVisita.ATENDEU) {
            throw new IllegalStateException("Fechamento só depois de uma visita em que o cliente atendeu");
        }

        String cnpj = CnpjUtils.normalizar(req.cnpj());
        Cliente cliente = clienteRepo.findByCnpj(cnpj).orElse(null);
        PerfilTributarioDTO perfil;
        if (cliente == null) {
            CnpjResponseDTO receita = clienteService.consultarCnpj(cnpj);
            perfil = receita.perfil();
            cliente = clienteService.criar(new ClienteRequest(
                    receita.cnpj(),
                    receita.razaoSocial(),
                    receita.nomeFantasia(),
                    receita.endereco(),
                    receita.cidade(),
                    receita.uf(),
                    receita.telefone(),
                    receita.email()));
        } else {
            perfil = perfilSemFalhar(cnpj);
        }

        p.setCliente(cliente);
        p.setStatus(StatusProspect.FECHADO);
        p.setFechadoEm(req.dataFechamento() != null ? req.dataFechamento() : LocalDate.now());
        if (req.observacao() != null && !req.observacao().isBlank()) {
            p.setObservacao(req.observacao());
        }
        repo.save(p);
        log.info("Prospect {} fechado: cliente {}", id, cliente.getId());
        return ProspectResponse.from(p, null, perfil);
    }

    private PerfilTributarioDTO perfilSemFalhar(String cnpj) {
        try {
            return clienteService.consultarCnpj(cnpj).perfil();
        } catch (ServicoIndisponivelException e) {
            log.warn("Selo de perfil do CNPJ {} indisponível: {}", cnpj, e.getMessage());
            return null;
        }
    }

    /**
     * Negociação perdida depois de uma visita em que o cliente atendeu.
     * Fica livre para outro comercial, como o descartado.
     */
    @Transactional
    public ProspectResponse perder(Long id, PerderProspectRequest req) {
        Prospect p = buscarVisivel(id);
        if (p.getStatus() != StatusProspect.VISITADO || p.getResultadoVisita() != ResultadoVisita.ATENDEU) {
            throw new IllegalStateException("Perdido só depois de uma visita em que o cliente atendeu");
        }
        p.setStatus(StatusProspect.PERDIDO);
        p.setMotivoPerda(req.motivo());
        p.setObservacaoPerda(req.observacao());
        return ProspectResponse.from(repo.save(p), null);
    }

    /** Levantamento do cliente fechado. As regras de Levantamento ficam no service dele. */
    @Transactional
    public LtoProspectResponse lto(Long id, LtoProspectRequest req) {
        Prospect p = buscarVisivel(id);
        if (p.getStatus() != StatusProspect.FECHADO) {
            throw new IllegalStateException("LTO só depois do contrato fechado");
        }

        Cliente cliente = p.getCliente();
        Levantamento levantamento = levantamentoService.criar(new LevantamentoRequest(
                cliente.getId(),
                req.qtdServidores(),
                req.qtdPdvs(),
                req.qtdRetaguardas(),
                req.consultaPreco(),
                req.qtdConsultaPreco(),
                req.outros()));

        p.setStatus(StatusProspect.LTO);
        repo.save(p);
        log.info("Prospect {} virou LTO: cliente {}, levantamento {}", id, cliente.getId(), levantamento.getId());
        return new LtoProspectResponse(cliente.getId(), levantamento.getId());
    }

    /** Prospect de outro usuário responde 404, igual a inexistente (não revela que existe). */
    private Prospect buscarVisivel(Long id) {
        Prospect p = repo.findById(id).orElse(null);
        if (p == null || !podeVer(p)) {
            throw new EntityNotFoundException("Prospect não encontrado");
        }
        return p;
    }

    private boolean podeVer(Prospect p) {
        UsuarioAutenticado atual = usuarioLogado.atual();
        return veTodos(atual) || p.getResponsavel().getId().equals(atual.id());
    }

    private static boolean veTodos(UsuarioAutenticado usuario) {
        return usuario.perfil() == Perfil.ADMIN || usuario.perfil() == Perfil.DIRETORIA;
    }
}
