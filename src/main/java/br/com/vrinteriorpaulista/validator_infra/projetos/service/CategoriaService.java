package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.CategoriaRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.CategoriaResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Categoria;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.CategoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Categoria não é apagada (atividades e templates apontam para ela): DELETE só inativa. */
@Service
public class CategoriaService {

    private final CategoriaRepository repo;

    public CategoriaService(CategoriaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(boolean apenasAtivas) {
        return (apenasAtivas ? repo.findByAtivoTrueOrderByNomeAsc() : repo.findAllByOrderByNomeAsc()).stream()
                .map(CategoriaResponse::from)
                .toList();
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest req) {
        verificarNome(req.nome(), null);
        Categoria c = new Categoria();
        preencher(c, req);
        return CategoriaResponse.from(repo.save(c));
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest req) {
        Categoria c = buscar(id);
        verificarNome(req.nome(), id);
        preencher(c, req);
        return CategoriaResponse.from(c);
    }

    @Transactional
    public void inativar(Long id) {
        buscar(id).setAtivo(false);
    }

    public Categoria buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));
    }

    private void verificarNome(String nome, Long id) {
        repo.findByNomeIgnoreCase(nome.trim())
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new IllegalStateException("Já existe a categoria " + outra.getNome());
                });
    }

    private static void preencher(Categoria c, CategoriaRequest req) {
        c.setNome(req.nome().trim());
        c.setHorasPadrao(req.horasPadrao());
        c.setAtivo(req.ativo() == null || req.ativo());
    }
}
