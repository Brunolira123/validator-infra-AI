package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.*;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.*;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.ContatoClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.PlanejamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.LevantamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Planejamento de implantação: dados, atividades (hierarquia, distribuição, dependências), deslocamentos, feriados
 * extras, situação, aplicar template e salvar como template. O cálculo fica no {@link CalculoPlanejamentoService}.
 * Toda edição marca o último cálculo como desatualizado.
 */
@Service
@Slf4j
public class PlanejamentoService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final PlanejamentoRepository repo;
    private final ClienteRepository clienteRepo;
    private final LevantamentoRepository levantamentoRepo;
    private final UsuarioRepository usuarioRepo;
    private final ContatoClienteRepository contatoRepo;
    private final CategoriaService categorias;
    private final TemplateService templates;
    private final CalculoPlanejamentoService calculos;
    private final AcessoProjetos acesso;
    private final BigDecimal horasDia;

    public PlanejamentoService(PlanejamentoRepository repo,
                               ClienteRepository clienteRepo,
                               LevantamentoRepository levantamentoRepo,
                               UsuarioRepository usuarioRepo,
                               ContatoClienteRepository contatoRepo,
                               CategoriaService categorias,
                               TemplateService templates,
                               CalculoPlanejamentoService calculos,
                               AcessoProjetos acesso,
                               @Value("${app.projetos.horas-dia}") BigDecimal horasDia) {
        this.repo = repo;
        this.clienteRepo = clienteRepo;
        this.levantamentoRepo = levantamentoRepo;
        this.usuarioRepo = usuarioRepo;
        this.contatoRepo = contatoRepo;
        this.categorias = categorias;
        this.templates = templates;
        this.calculos = calculos;
        this.acesso = acesso;
        this.horasDia = horasDia;
    }

    // ----- planejamento -----

    @Transactional(readOnly = true)
    public PaginaPlanejamentosResponse listar(SituacaoPlanejamento situacao, Long responsavelId, String busca,
                                              int pagina, int tamanho) {
        Set<Long> clientes = acesso.clientesVisiveis();
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_PAGINA)),
                Sort.by(Sort.Order.desc("atualizadoEm"), Sort.Order.desc("id")));
        if (clientes != null && clientes.isEmpty()) {
            return new PaginaPlanejamentosResponse(List.of(), pageRequest.getPageNumber(), 0, 0);
        }
        Specification<Planejamento> filtro = (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (clientes != null) {
                condicoes.add(root.get("cliente").get("id").in(clientes));
            }
            if (situacao != null) {
                condicoes.add(cb.equal(root.get("situacao"), situacao));
            }
            if (responsavelId != null) {
                condicoes.add(cb.equal(root.get("responsavel").get("id"), responsavelId));
            }
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
                condicoes.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), termo),
                        cb.like(cb.lower(root.get("cliente").get("razaoSocial")), termo),
                        cb.like(cb.lower(cb.coalesce(root.get("cliente").get("nomeFantasia"), "")), termo)));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
        Page<Planejamento> page = repo.findAll(filtro, pageRequest);
        return new PaginaPlanejamentosResponse(page.getContent().stream().map(PlanejamentoService::resumo).toList(),
                page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlanejamentoResponse detalhar(Long id) {
        return resposta(acesso.visivel(id));
    }

    @Transactional
    public PlanejamentoResponse criar(PlanejamentoCriacaoRequest req) {
        Planejamento p = new Planejamento();
        p.setCliente(clienteRepo.findById(req.clienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado")));
        p.setSituacao(SituacaoPlanejamento.RASCUNHO);
        preencherDados(p, new PlanejamentoDadosRequest(req.nome(), req.levantamentoId(), req.kickoff(),
                req.horasContratadas(), req.custoHoraServico(), req.custoHoraViagem(), req.responsavelId(),
                req.observacao()));
        return resposta(repo.save(p));
    }

    /**
     * Rascunho criado depois do commit do LTO (transação própria). Idempotente por levantamento.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void criarRascunhoDoLto(Long clienteId, Long levantamentoId) {
        if (repo.existsByLevantamentoId(levantamentoId)) {
            log.info("Levantamento {} já tem planejamento: nada a criar", levantamentoId);
            return;
        }
        Cliente cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("Cliente " + clienteId + " não encontrado"));
        Planejamento p = new Planejamento();
        p.setCliente(cliente);
        p.setLevantamento(levantamentoRepo.findById(levantamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Levantamento " + levantamentoId + " não encontrado")));
        p.setNome("Implantação " + nomeCliente(cliente));
        p.setSituacao(SituacaoPlanejamento.RASCUNHO);
        p.setObservacao("Criado pelo LTO (levantamento " + levantamentoId + ")");
        repo.save(p);
        log.info("Planejamento {} criado em RASCUNHO pelo LTO: cliente {}, levantamento {}", p.getId(), clienteId,
                levantamentoId);
    }

    @Transactional
    public PlanejamentoResponse atualizarDados(Long id, PlanejamentoDadosRequest req) {
        Planejamento p = acesso.editavel(id);
        preencherDados(p, req);
        p.marcarAlterado();
        return resposta(p);
    }

    /**
     * RASCUNHO/EM_PLANEJAMENTO → EM_PLANEJAMENTO. CRIADO_ARTIA e CANCELADO são somente leitura e ENVIADO precisa
     * voltar para EM_PLANEJAMENTO antes (o cliente já recebeu as atividades).
     */
    @Transactional
    public PlanejamentoResponse mudarSituacao(Long id, SituacaoRequest req) {
        Planejamento p = acesso.visivel(id);
        SituacaoPlanejamento destino = req.situacao();
        if (p.getSituacao() == destino) {
            return resposta(p);
        }
        if (!p.getSituacao().podeIrPara(destino)) {
            throw new IllegalStateException("Não é possível passar de " + p.getSituacao() + " para " + destino);
        }
        if (destino == SituacaoPlanejamento.ENVIADO
                && (p.getCalculo() == null || Boolean.TRUE.equals(p.getCalculoDesatualizado()))) {
            throw new IllegalStateException("Calcule o planejamento antes de enviar");
        }
        log.info("Planejamento {}: {} → {}", p.getId(), p.getSituacao(), destino);
        p.setSituacao(destino);
        return resposta(p);
    }

    // ----- templates -----

    @Transactional
    public PlanejamentoResponse aplicarTemplate(Long id, AplicarTemplateRequest req) {
        Planejamento p = acesso.editavel(id);
        if (p.getSituacao() != SituacaoPlanejamento.RASCUNHO && p.getSituacao() != SituacaoPlanejamento.EM_PLANEJAMENTO) {
            throw new IllegalStateException("Template só pode ser aplicado em RASCUNHO ou EM_PLANEJAMENTO");
        }
        Template t = templates.buscar(req.templateId());
        if (!Boolean.TRUE.equals(t.getAtivo())) {
            throw new IllegalStateException("Template " + t.getNome() + " está inativo");
        }
        if (!p.getAtividades().isEmpty()) {
            if (!Boolean.TRUE.equals(req.substituir())) {
                throw new IllegalStateException("O planejamento já tem atividades: mande substituir=true para trocá-las");
            }
            removerAtividades(p, new HashSet<>(p.getAtividades()));
        }

        Map<String, PlanejamentoAtividade> porChave = new HashMap<>();
        int ordem = 0;
        for (TemplateAtividade ta : t.getAtividades()) {
            PlanejamentoAtividade a = new PlanejamentoAtividade();
            a.setPlanejamento(p);
            a.setNome(ta.getNome());
            a.setCategoria(ta.getCategoria());
            a.setHoras(ta.getHoras() != null ? ta.getHoras()
                    : ta.getCategoria() != null ? ta.getCategoria().getHorasPadrao() : BigDecimal.ZERO);
            a.setIndivisivel(Boolean.TRUE.equals(ta.getIndivisivel()));
            a.setDoCliente(Boolean.TRUE.equals(ta.getDoCliente()));
            a.setDiasCliente(ta.getDiasCliente());
            a.setOrdem(ordem++);
            porChave.put(ta.getChave(), a);
            p.getAtividades().add(a);
        }
        // Grava antes de ligar pai e dependências: as atividades novas apontam umas para as outras
        repo.flush();
        for (TemplateAtividade ta : t.getAtividades()) {
            PlanejamentoAtividade a = porChave.get(ta.getChave());
            a.setPai(ta.getChavePai() != null ? porChave.get(ta.getChavePai()) : null);
            ta.getDependeDe().stream().map(porChave::get).filter(d -> d != null).forEach(a.getDependeDe()::add);
        }
        p.setTemplateNome(t.getNome());
        p.setTemplateVersao(t.getVersao());
        p.setSituacao(SituacaoPlanejamento.EM_PLANEJAMENTO);
        p.marcarAlterado();
        repo.flush();
        log.info("Template {} v{} aplicado no planejamento {}: {} atividades", t.getNome(), t.getVersao(), p.getId(),
                porChave.size());
        return resposta(p);
    }

    /**
     * Com templateId, vira a próxima versão desse template (o nome dele é mantido); sem, cria um template novo.
     * horas ficam null quando iguais ao padrão da categoria, para seguir a categoria ao aplicar.
     */
    @Transactional
    public Salvo salvarComoTemplate(Long id, SalvarComoTemplateRequest req) {
        Planejamento p = acesso.visivel(id);
        if (p.getAtividades().isEmpty()) {
            throw new IllegalStateException("O planejamento não tem atividades");
        }
        Map<Long, String> chaves = new HashMap<>();
        int n = 1;
        for (PlanejamentoAtividade a : p.getAtividades()) {
            chaves.put(a.getId(), "a" + n++);
        }
        List<TemplateAtividadeRequest> atividades = new ArrayList<>();
        int ordem = 0;
        for (PlanejamentoAtividade a : p.getAtividades()) {
            boolean segueCategoria = a.getCategoria() != null && a.getCategoria().getHorasPadrao().compareTo(a.getHoras()) == 0;
            atividades.add(new TemplateAtividadeRequest(
                    chaves.get(a.getId()),
                    a.getPai() != null ? chaves.get(a.getPai().getId()) : null,
                    a.getNome(),
                    a.getCategoria() != null ? a.getCategoria().getId() : null,
                    segueCategoria ? null : a.getHoras(),
                    a.getDependeDe().stream().sorted(Comparator.comparing(PlanejamentoAtividade::getOrdem)
                            .thenComparing(PlanejamentoAtividade::getId)).map(d -> chaves.get(d.getId())).toList(),
                    a.getIndivisivel(),
                    a.getDoCliente(),
                    a.getDiasCliente(),
                    ordem++));
        }
        if (req.templateId() != null) {
            Template t = templates.buscar(req.templateId());
            TemplateResponse novo = templates.atualizar(t.getId(), new TemplateRequest(t.getNome(), t.getAtivo(), atividades));
            log.info("Planejamento {} salvo como versão {} do template {}", p.getId(), novo.versao(), novo.nome());
            return new Salvo(novo, false);
        }
        if (req.nome() == null || req.nome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do template novo ou o templateId para uma nova versão");
        }
        TemplateResponse novo = templates.criar(new TemplateRequest(req.nome(), true, atividades));
        log.info("Planejamento {} salvo como o template {} ({})", p.getId(), novo.nome(), novo.id());
        return new Salvo(novo, true);
    }

    public record Salvo(TemplateResponse template, boolean criado) {}

    // ----- atividades -----

    @Transactional
    public AtividadeResponse criarAtividade(Long id, AtividadeRequest req) {
        Planejamento p = acesso.editavel(id);
        PlanejamentoAtividade a = new PlanejamentoAtividade();
        a.setPlanejamento(p);
        preencherAtividade(p, a, req, true);
        p.getAtividades().add(a);
        p.marcarAlterado();
        repo.flush();
        return atividade(a);
    }

    @Transactional
    public AtividadeResponse atualizarAtividade(Long id, Long atividadeId, AtividadeRequest req) {
        Planejamento p = acesso.editavel(id);
        PlanejamentoAtividade a = atividadeDo(p, atividadeId);
        preencherAtividade(p, a, req, false);
        p.marcarAlterado();
        return atividade(a);
    }

    /** Apaga a atividade e as subtarefas dela; as dependências que apontavam para elas somem. */
    @Transactional
    public void excluirAtividade(Long id, Long atividadeId) {
        Planejamento p = acesso.editavel(id);
        PlanejamentoAtividade a = atividadeDo(p, atividadeId);
        Set<PlanejamentoAtividade> subarvore = new HashSet<>();
        coletar(p, a, subarvore);
        removerAtividades(p, subarvore);
        p.marcarAlterado();
    }

    /** Lista vazia = todas as horas para o responsável. A soma é conferida no cálculo. */
    @Transactional
    public AtividadeResponse definirDistribuicao(Long id, Long atividadeId, List<DivisaoRequest> divisoes) {
        Planejamento p = acesso.editavel(id);
        PlanejamentoAtividade a = atividadeDo(p, atividadeId);
        if (temFilhos(p, a)) {
            throw new IllegalStateException("Atividade com subtarefas é resumo: distribua as horas nas subtarefas");
        }
        if (Boolean.TRUE.equals(a.getDoCliente())) {
            throw new IllegalStateException("Atividade do cliente não usa analista");
        }
        Set<Long> vistos = new HashSet<>();
        List<Distribuicao> nova = new ArrayList<>();
        for (DivisaoRequest d : divisoes) {
            if (!vistos.add(d.usuarioId())) {
                throw new IllegalArgumentException("Analista repetido na distribuição");
            }
            nova.add(new Distribuicao(usuario(d.usuarioId()), d.horas()));
        }
        a.getDistribuicao().clear();
        a.getDistribuicao().addAll(nova);
        p.marcarAlterado();
        return atividade(a);
    }

    /** Ciclos são apontados no cálculo, junto com os outros erros. */
    @Transactional
    public AtividadeResponse definirDependencias(Long id, Long atividadeId, DependenciasRequest req) {
        Planejamento p = acesso.editavel(id);
        PlanejamentoAtividade a = atividadeDo(p, atividadeId);
        Set<PlanejamentoAtividade> deps = new LinkedHashSet<>();
        for (Long depId : req.dependeDe()) {
            if (depId.equals(atividadeId)) {
                throw new IllegalArgumentException("A atividade não pode depender de si mesma");
            }
            deps.add(atividadeDo(p, depId, "Dependência " + depId + " não pertence ao planejamento"));
        }
        a.getDependeDe().clear();
        a.getDependeDe().addAll(deps);
        p.marcarAlterado();
        return atividade(a);
    }

    // ----- deslocamentos e feriados extras -----

    @Transactional
    public DeslocamentoResponse criarDeslocamento(Long id, DeslocamentoRequest req) {
        Planejamento p = acesso.editavel(id);
        Deslocamento d = new Deslocamento();
        d.setPlanejamento(p);
        preencherDeslocamento(d, req);
        p.getDeslocamentos().add(d);
        p.marcarAlterado();
        repo.flush();
        return deslocamento(d);
    }

    @Transactional
    public DeslocamentoResponse atualizarDeslocamento(Long id, Long deslocamentoId, DeslocamentoRequest req) {
        Planejamento p = acesso.editavel(id);
        Deslocamento d = deslocamentoDo(p, deslocamentoId);
        preencherDeslocamento(d, req);
        p.marcarAlterado();
        return deslocamento(d);
    }

    @Transactional
    public void excluirDeslocamento(Long id, Long deslocamentoId) {
        Planejamento p = acesso.editavel(id);
        p.getDeslocamentos().remove(deslocamentoDo(p, deslocamentoId));
        p.marcarAlterado();
    }

    @Transactional
    public FeriadoExtraResponse criarFeriadoExtra(Long id, FeriadoExtraRequest req) {
        Planejamento p = acesso.editavel(id);
        if (p.getFeriadosExtras().stream().anyMatch(f -> f.getData().equals(req.data()))) {
            throw new IllegalStateException("Essa data já é feriado extra do planejamento");
        }
        FeriadoExtra f = new FeriadoExtra();
        f.setPlanejamento(p);
        f.setData(req.data());
        f.setDescricao(req.descricao());
        p.getFeriadosExtras().add(f);
        p.marcarAlterado();
        repo.flush();
        return new FeriadoExtraResponse(f.getId(), f.getData(), f.getDescricao());
    }

    @Transactional
    public void excluirFeriadoExtra(Long id, Long feriadoId) {
        Planejamento p = acesso.editavel(id);
        FeriadoExtra f = p.getFeriadosExtras().stream().filter(x -> x.getId().equals(feriadoId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Feriado extra não encontrado"));
        p.getFeriadosExtras().remove(f);
        p.marcarAlterado();
    }

    // ----- apoio -----

    private void preencherDados(Planejamento p, PlanejamentoDadosRequest req) {
        p.setNome(req.nome().trim());
        if (req.levantamentoId() != null) {
            Levantamento l = levantamentoRepo.findById(req.levantamentoId())
                    .orElseThrow(() -> new IllegalArgumentException("Levantamento não encontrado"));
            if (!l.getCliente().getId().equals(p.getCliente().getId())) {
                throw new IllegalArgumentException("O levantamento é de outro cliente");
            }
            p.setLevantamento(l);
        } else {
            p.setLevantamento(null);
        }
        p.setKickoff(req.kickoff());
        p.setHorasContratadas(req.horasContratadas());
        p.setCustoHoraServico(req.custoHoraServico());
        p.setCustoHoraViagem(req.custoHoraViagem());
        p.setResponsavel(req.responsavelId() != null ? usuario(req.responsavelId()) : null);
        p.setObservacao(req.observacao());
    }

    private void preencherAtividade(Planejamento p, PlanejamentoAtividade a, AtividadeRequest req, boolean nova) {
        a.setNome(req.nome().trim());
        if (req.paiId() != null) {
            PlanejamentoAtividade pai = atividadeDo(p, req.paiId(), "Atividade principal não pertence ao planejamento");
            if (!nova && ehDescendenteOuPropria(pai, a)) {
                throw new IllegalArgumentException("A atividade principal não pode ser a própria atividade nem uma subtarefa dela");
            }
            a.setPai(pai);
        } else {
            a.setPai(null);
        }
        Categoria categoria = req.categoriaId() != null ? categorias.buscar(req.categoriaId()) : null;
        a.setCategoria(categoria);
        a.setHoras(req.horas() != null ? req.horas()
                : categoria != null ? categoria.getHorasPadrao() : BigDecimal.ZERO);
        a.setResponsavel(req.responsavelId() != null ? usuario(req.responsavelId()) : null);
        a.setIndivisivel(Boolean.TRUE.equals(req.indivisivel()));
        a.setDoCliente(Boolean.TRUE.equals(req.doCliente()));
        a.setDiasCliente(req.diasCliente() != null ? req.diasCliente() : 1);
        if (req.contatoClienteId() != null) {
            if (!a.getDoCliente()) {
                throw new IllegalArgumentException("Contato do cliente só em atividade do cliente (doCliente)");
            }
            ContatoCliente contato = contatoRepo.findById(req.contatoClienteId())
                    .filter(c -> c.getCliente().getId().equals(p.getCliente().getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Contato não encontrado neste cliente"));
            if (!Boolean.TRUE.equals(contato.getAtivo())) {
                throw new IllegalArgumentException("Contato " + contato.getNome() + " está inativo");
            }
            a.setContatoCliente(contato);
        } else {
            a.setContatoCliente(null);
        }
        if (a.getDoCliente()) {
            a.getDistribuicao().clear();
        }
        if (req.ordem() != null) {
            a.setOrdem(req.ordem());
        } else if (nova) {
            a.setOrdem(p.getAtividades().stream().mapToInt(PlanejamentoAtividade::getOrdem).max().orElse(-1) + 1);
        }
    }

    private void preencherDeslocamento(Deslocamento d, DeslocamentoRequest req) {
        if (req.horas().compareTo(horasDia) > 0) {
            throw new IllegalArgumentException("Horas do deslocamento devem ficar entre 0 e "
                    + horasDia.stripTrailingZeros().toPlainString() + "h");
        }
        d.setUsuario(usuario(req.usuarioId()));
        d.setData(req.data());
        d.setHoras(req.horas());
        d.setDescricao(req.descricao());
    }

    /** Desliga pai e dependências antes de apagar, para o banco não barrar pelas chaves estrangeiras. */
    private void removerAtividades(Planejamento p, Set<PlanejamentoAtividade> remover) {
        for (PlanejamentoAtividade x : p.getAtividades()) {
            x.getDependeDe().removeAll(remover);
            if (remover.contains(x)) {
                x.setPai(null);
                x.getDependeDe().clear();
            }
        }
        repo.flush();
        p.getAtividades().removeAll(remover);
        repo.flush();
    }

    private static void coletar(Planejamento p, PlanejamentoAtividade a, Set<PlanejamentoAtividade> saida) {
        if (!saida.add(a)) return;
        for (PlanejamentoAtividade x : p.getAtividades()) {
            if (x.getPai() == a) {
                coletar(p, x, saida);
            }
        }
    }

    private static boolean ehDescendenteOuPropria(PlanejamentoAtividade candidato, PlanejamentoAtividade a) {
        Set<PlanejamentoAtividade> vistos = new HashSet<>();
        for (PlanejamentoAtividade atual = candidato; atual != null && vistos.add(atual); atual = atual.getPai()) {
            if (atual == a) return true;
        }
        return false;
    }

    private static boolean temFilhos(Planejamento p, PlanejamentoAtividade a) {
        return p.getAtividades().stream().anyMatch(x -> x.getPai() == a);
    }

    private static PlanejamentoAtividade atividadeDo(Planejamento p, Long atividadeId) {
        return p.getAtividades().stream().filter(a -> a.getId().equals(atividadeId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada"));
    }

    private static PlanejamentoAtividade atividadeDo(Planejamento p, Long atividadeId, String erro) {
        return p.getAtividades().stream().filter(a -> a.getId().equals(atividadeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(erro));
    }

    private static Deslocamento deslocamentoDo(Planejamento p, Long deslocamentoId) {
        return p.getDeslocamentos().stream().filter(d -> d.getId().equals(deslocamentoId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Deslocamento não encontrado"));
    }

    private Usuario usuario(Long id) {
        Usuario u = usuarioRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Usuário " + id + " não encontrado"));
        if (!Boolean.TRUE.equals(u.getAtivo())) {
            throw new IllegalArgumentException("Usuário " + u.getNome() + " está inativo");
        }
        return u;
    }

    private static String nomeCliente(Cliente c) {
        return c.getNomeFantasia() != null && !c.getNomeFantasia().isBlank() ? c.getNomeFantasia() : c.getRazaoSocial();
    }

    private static PlanejamentoResumoResponse resumo(Planejamento p) {
        return new PlanejamentoResumoResponse(p.getId(), p.getCliente().getId(), nomeCliente(p.getCliente()),
                p.getLevantamento() != null ? p.getLevantamento().getId() : null, p.getNome(), p.getSituacao(),
                p.getTemplateNome(), p.getTemplateVersao(), p.getKickoff(), p.getInicioCalculado(),
                p.getTerminoCalculado(),
                p.getResponsavel() != null ? p.getResponsavel().getId() : null,
                p.getResponsavel() != null ? p.getResponsavel().getNome() : null,
                p.getCalculadoEm(), Boolean.TRUE.equals(p.getCalculoDesatualizado()), p.getAtualizadoEm());
    }

    private PlanejamentoResponse resposta(Planejamento p) {
        Cliente c = p.getCliente();
        return new PlanejamentoResponse(p.getId(), c.getId(), nomeCliente(c), c.getCidade(), c.getUf(),
                p.getLevantamento() != null ? p.getLevantamento().getId() : null, p.getNome(), p.getSituacao(),
                p.getTemplateNome(), p.getTemplateVersao(), p.getKickoff(), p.getHorasContratadas(),
                p.getCustoHoraServico(), p.getCustoHoraViagem(),
                p.getResponsavel() != null ? p.getResponsavel().getId() : null,
                p.getResponsavel() != null ? p.getResponsavel().getNome() : null,
                p.getObservacao(),
                p.getAtividades().stream().map(PlanejamentoService::atividade).toList(),
                p.getDeslocamentos().stream().map(PlanejamentoService::deslocamento).toList(),
                p.getFeriadosExtras().stream().map(f -> new FeriadoExtraResponse(f.getId(), f.getData(), f.getDescricao())).toList(),
                p.getCalculo() != null ? calculos.ler(p.getCalculo()) : null,
                Boolean.TRUE.equals(p.getCalculoDesatualizado()), p.getCriadoEm(), p.getAtualizadoEm());
    }

    private static AtividadeResponse atividade(PlanejamentoAtividade a) {
        return new AtividadeResponse(a.getId(), a.getPai() != null ? a.getPai().getId() : null, a.getNome(),
                a.getCategoria() != null ? a.getCategoria().getId() : null,
                a.getCategoria() != null ? a.getCategoria().getNome() : null,
                a.getHoras(),
                a.getResponsavel() != null ? a.getResponsavel().getId() : null,
                a.getResponsavel() != null ? a.getResponsavel().getNome() : null,
                Boolean.TRUE.equals(a.getIndivisivel()), Boolean.TRUE.equals(a.getDoCliente()), a.getDiasCliente(),
                a.getContatoCliente() != null ? a.getContatoCliente().getId() : null,
                a.getContatoCliente() != null ? a.getContatoCliente().getNome() : null,
                a.getOrdem(),
                a.getDependeDe().stream().map(PlanejamentoAtividade::getId).sorted().toList(),
                a.getDistribuicao().stream()
                        .map(d -> new DivisaoResponse(d.getUsuario().getId(), d.getUsuario().getNome(), d.getHoras()))
                        .toList());
    }

    private static DeslocamentoResponse deslocamento(Deslocamento d) {
        return new DeslocamentoResponse(d.getId(), d.getUsuario().getId(), d.getUsuario().getNome(), d.getData(),
                d.getHoras(), d.getDescricao());
    }
}
