package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.ContatoClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.ContatoClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.ContatoCliente;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.ContatoClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Contato não é apagado (atividades do cliente apontam para ele): DELETE só inativa. */
@Service
public class ContatoClienteService {

    private final ContatoClienteRepository repo;
    private final ClienteRepository clienteRepo;

    public ContatoClienteService(ContatoClienteRepository repo, ClienteRepository clienteRepo) {
        this.repo = repo;
        this.clienteRepo = clienteRepo;
    }

    @Transactional(readOnly = true)
    public List<ContatoClienteResponse> listar(Long clienteId, boolean apenasAtivos) {
        cliente(clienteId);
        return (apenasAtivos ? repo.findByClienteIdAndAtivoTrueOrderByNomeAsc(clienteId)
                : repo.findByClienteIdOrderByNomeAsc(clienteId)).stream()
                .map(ContatoClienteResponse::from)
                .toList();
    }

    @Transactional
    public ContatoClienteResponse criar(Long clienteId, ContatoClienteRequest req) {
        ContatoCliente c = new ContatoCliente();
        c.setCliente(cliente(clienteId));
        preencher(c, req);
        return ContatoClienteResponse.from(repo.save(c));
    }

    @Transactional
    public ContatoClienteResponse atualizar(Long id, ContatoClienteRequest req) {
        ContatoCliente c = buscar(id);
        preencher(c, req);
        return ContatoClienteResponse.from(c);
    }

    @Transactional
    public void inativar(Long id) {
        buscar(id).setAtivo(false);
    }

    private ContatoCliente buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Contato não encontrado"));
    }

    private Cliente cliente(Long id) {
        return clienteRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
    }

    private static void preencher(ContatoCliente c, ContatoClienteRequest req) {
        c.setNome(req.nome().trim());
        c.setEmail(req.email());
        c.setTelefone(req.telefone());
        c.setSetor(req.setor());
        c.setAtivo(req.ativo() == null || req.ativo());
    }
}
