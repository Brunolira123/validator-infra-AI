package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateAtividadeRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateResumoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Template;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.TemplateAtividade;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.TemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Templates de atividades. Cada salvamento (PUT ou "salvar planejamento como nova versão") troca as atividades e
 * sobe a versão. Planejamentos guardam só o nome e a versão aplicados, então mudar o template não os afeta.
 * DELETE só inativa.
 */
@Service
public class TemplateService {

    private final TemplateRepository repo;
    private final CategoriaService categorias;

    public TemplateService(TemplateRepository repo, CategoriaService categorias) {
        this.repo = repo;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<TemplateResumoResponse> listar(boolean apenasAtivos) {
        return (apenasAtivos ? repo.findByAtivoTrueOrderByNomeAsc() : repo.findAllByOrderByNomeAsc()).stream()
                .map(TemplateResumoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse detalhar(Long id) {
        return TemplateResponse.from(buscar(id));
    }

    @Transactional
    public TemplateResponse criar(TemplateRequest req) {
        verificarNome(req.nome(), null);
        Template t = new Template();
        t.setVersao(1);
        preencher(t, req);
        return TemplateResponse.from(repo.saveAndFlush(t));
    }

    @Transactional
    public TemplateResponse atualizar(Long id, TemplateRequest req) {
        Template t = buscar(id);
        verificarNome(req.nome(), id);
        t.getAtividades().clear();
        repo.flush();
        t.setVersao(t.getVersao() + 1);
        preencher(t, req);
        repo.flush();
        return TemplateResponse.from(t);
    }

    @Transactional
    public void inativar(Long id) {
        buscar(id).setAtivo(false);
    }

    public Template buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Template não encontrado"));
    }

    private void verificarNome(String nome, Long id) {
        repo.findByNomeIgnoreCase(nome.trim())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new IllegalStateException("Já existe o template " + outro.getNome());
                });
    }

    private void preencher(Template t, TemplateRequest req) {
        validar(req.atividades());
        t.setNome(req.nome().trim());
        t.setAtivo(req.ativo() == null || req.ativo());
        int posicao = 0;
        for (TemplateAtividadeRequest a : req.atividades()) {
            TemplateAtividade ta = new TemplateAtividade();
            ta.setTemplate(t);
            ta.setChave(a.chave().trim());
            ta.setChavePai(vazioParaNull(a.chavePai()));
            ta.setNome(a.nome().trim());
            ta.setCategoria(a.categoriaId() != null ? categorias.buscar(a.categoriaId()) : null);
            ta.setHoras(a.horas());
            ta.setDependeDe(a.dependeDe() == null ? new ArrayList<>()
                    : new ArrayList<>(new LinkedHashSet<>(a.dependeDe().stream().map(String::trim).toList())));
            ta.setIndivisivel(Boolean.TRUE.equals(a.indivisivel()));
            ta.setDoCliente(Boolean.TRUE.equals(a.doCliente()));
            ta.setDiasCliente(a.diasCliente() != null ? a.diasCliente() : 1);
            ta.setOrdem(a.ordem() != null ? a.ordem() : posicao);
            posicao++;
            t.getAtividades().add(ta);
        }
    }

    /** Chaves únicas; pai e dependências apontam para chaves do template; hierarquia sem ciclo. */
    private static void validar(List<TemplateAtividadeRequest> atividades) {
        List<String> erros = new ArrayList<>();
        Map<String, String> paiDe = new HashMap<>();
        for (TemplateAtividadeRequest a : atividades) {
            if (paiDe.containsKey(a.chave().trim())) {
                erros.add("Chave repetida: " + a.chave().trim());
            }
            paiDe.put(a.chave().trim(), vazioParaNull(a.chavePai()));
        }
        for (TemplateAtividadeRequest a : atividades) {
            String chave = a.chave().trim();
            String pai = vazioParaNull(a.chavePai());
            if (pai != null && !paiDe.containsKey(pai)) {
                erros.add("Atividade " + chave + ": atividade principal " + pai + " não existe no template");
            }
            for (String dep : a.dependeDe() == null ? List.<String>of() : a.dependeDe()) {
                if (dep.trim().equals(chave)) {
                    erros.add("Atividade " + chave + ": não pode depender de si mesma");
                } else if (!paiDe.containsKey(dep.trim())) {
                    erros.add("Atividade " + chave + ": dependência " + dep.trim() + " não existe no template");
                }
            }
            Set<String> caminho = new HashSet<>();
            for (String atual = chave; atual != null; atual = paiDe.get(atual)) {
                if (!caminho.add(atual)) {
                    erros.add("Atividade " + chave + ": ciclo de subtarefas");
                    break;
                }
            }
        }
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", new LinkedHashSet<>(erros)));
        }
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
