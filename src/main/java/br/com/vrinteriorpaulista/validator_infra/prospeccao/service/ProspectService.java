package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.ClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.request.LevantamentoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.CnpjResponseDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Funil do prospect de campo: salvar da busca, registrar visita, descartar e LTO (vira Cliente + Levantamento).
 * COMERCIAL (e demais perfis) só enxerga os prospects que salvou; ADMIN e DIRETORIA veem todos.
 */
@Service
@Slf4j
public class ProspectService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final ProspectRepository repo;
    private final VisitaProspectRepository visitaRepo;
    private final ClienteRepository clienteRepo;
    private final ClienteService clienteService;
    private final LevantamentoService levantamentoService;
    private final ProspeccaoService prospeccao;
    private final UsuarioLogadoService usuarioLogado;

    public ProspectService(ProspectRepository repo,
                           VisitaProspectRepository visitaRepo,
                           ClienteRepository clienteRepo,
                           ClienteService clienteService,
                           LevantamentoService levantamentoService,
                           ProspeccaoService prospeccao,
                           UsuarioLogadoService usuarioLogado) {
        this.repo = repo;
        this.visitaRepo = visitaRepo;
        this.clienteRepo = clienteRepo;
        this.clienteService = clienteService;
        this.levantamentoService = levantamentoService;
        this.prospeccao = prospeccao;
        this.usuarioLogado = usuarioLogado;
    }

    /** Resultado do salvar: o front recebe 201 quando criou e 200 quando o lugar já era prospect. */
    public record Salvo(ProspectResponse prospect, boolean criado) {}

    /**
     * Idempotente por placeId: se já existe e o usuário enxerga, devolve o existente.
     * Se é de outro comercial, 409 (não dá para dois trabalharem o mesmo lugar sem saber).
     */
    @Transactional
    public Salvo salvar(SalvarProspectRequest req) {
        Prospect existente = repo.findByPlaceIdIncluindoExcluidos(req.placeId()).orElse(null);
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
            p.setResultadoVisita(null);
            p.setTemperatura(null);
            p.setVisitadoEm(null);
            p.setObservacao(null);
            p.setCliente(null);
        }
        p.setOrigem(OrigemProspect.GOOGLE);
        p.setPlaceId(req.placeId());
        p.setCidade(req.cidade().trim());
        p.setUf(req.uf().trim().toUpperCase(Locale.ROOT));
        p.setCategoria(req.categoria());
        p.setStatus(StatusProspect.NOVO);
        p.setResponsavel(usuarioLogado.usuario());

        return new Salvo(ProspectResponse.from(repo.save(p), null), true);
    }

    /** Lista paginada. O Google (Place Details) só é consultado para a página devolvida. */
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

    /** Pode haver várias visitas: cada uma grava histórico e a última define o prospect. */
    @Transactional
    public ProspectResponse registrarVisita(Long id, VisitaProspectRequest req) {
        if (req.resultado() == ResultadoVisita.ATENDEU && req.temperatura() == null) {
            throw new IllegalArgumentException("Informe a temperatura da negociação quando o cliente atendeu");
        }
        if (req.resultado() == ResultadoVisita.NAO_ATENDEU && req.temperatura() != null) {
            throw new IllegalArgumentException("Temperatura só vale quando o cliente atendeu");
        }
        Prospect p = buscarVisivel(id);
        if (p.getStatus() == StatusProspect.LTO) {
            throw new IllegalStateException("Prospect já virou LTO");
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
        return ProspectResponse.from(repo.save(p), null);
    }

    @Transactional
    public ProspectResponse descartar(Long id) {
        Prospect p = buscarVisivel(id);
        if (p.getStatus() == StatusProspect.LTO) {
            throw new IllegalStateException("Prospect que já virou LTO não pode ser descartado");
        }
        p.setStatus(StatusProspect.DESCARTADO);
        return ProspectResponse.from(repo.save(p), null);
    }

    /**
     * Prospect visitado e que atendeu vira Cliente (só com dados da Receita) + Levantamento, numa transação.
     * Reaproveita o Cliente se o CNPJ já estiver cadastrado. As regras de Cliente e Levantamento ficam nos
     * services deles. O selo de perfil não bloqueia o LTO.
     */
    @Transactional
    public LtoProspectResponse lto(Long id, LtoProspectRequest req) {
        Prospect p = buscarVisivel(id);
        if (p.getStatus() != StatusProspect.VISITADO || p.getResultadoVisita() != ResultadoVisita.ATENDEU) {
            throw new IllegalStateException("LTO só depois de uma visita em que o cliente atendeu");
        }

        String cnpj = CnpjUtils.normalizar(req.cnpj());
        Cliente cliente = clienteRepo.findByCnpj(cnpj).orElseGet(() -> {
            CnpjResponseDTO receita = clienteService.consultarCnpj(cnpj);
            return clienteService.criar(new ClienteRequest(
                    receita.cnpj(),
                    receita.razaoSocial(),
                    receita.nomeFantasia(),
                    receita.endereco(),
                    receita.cidade(),
                    receita.uf(),
                    receita.telefone(),
                    receita.email()));
        });

        Levantamento levantamento = levantamentoService.criar(new LevantamentoRequest(
                cliente.getId(),
                req.qtdServidores(),
                req.qtdPdvs(),
                req.qtdRetaguardas(),
                req.consultaPreco(),
                req.qtdConsultaPreco(),
                req.outros()));

        p.setCliente(cliente);
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
