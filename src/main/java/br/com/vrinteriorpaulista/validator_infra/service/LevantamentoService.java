package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.LevantamentoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.EquipamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.entity.*;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusLevantamento;
import br.com.vrinteriorpaulista.validator_infra.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LevantamentoService {

    private final LevantamentoRepository levantamentoRepo;
    private final ClienteRepository clienteRepo;
    private final EquipamentoRepository equipamentoRepo;
    private final UsuarioLogadoService usuarioLogado;

    public LevantamentoService(LevantamentoRepository levantamentoRepo,
                               ClienteRepository clienteRepo,
                               EquipamentoRepository equipamentoRepo,
                               UsuarioLogadoService usuarioLogado) {
        this.levantamentoRepo = levantamentoRepo;
        this.clienteRepo = clienteRepo;
        this.equipamentoRepo = equipamentoRepo;
        this.usuarioLogado = usuarioLogado;
    }

    @Transactional
    public Levantamento criar(LevantamentoRequest req) {
        Cliente cliente = clienteRepo.findById(req.clienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
        Usuario usuario = usuarioLogado.usuario();

        Levantamento l = new Levantamento();
        l.setCliente(cliente);
        l.setUsuario(usuario);
        l.setQtdServidores(req.qtdServidores() != null ? req.qtdServidores() : 0);
        l.setQtdPdvs(req.qtdPdvs() != null ? req.qtdPdvs() : 0);
        l.setQtdRetaguardas(req.qtdRetaguardas() != null ? req.qtdRetaguardas() : 0);
        l.setConsultaPreco(Boolean.TRUE.equals(req.consultaPreco()));
        l.setQtdConsultaPreco(req.qtdConsultaPreco() != null ? req.qtdConsultaPreco() : 0);
        l.setOutros(req.outros());
        l.setStatus(StatusLevantamento.RASCUNHO);

        return levantamentoRepo.save(l);
    }

    @Transactional
    public Levantamento gerarEquipamentos(Long levantamentoId) {
        Levantamento l = buscar(levantamentoId);

        if (l.getStatus() != StatusLevantamento.RASCUNHO) {
            throw new IllegalStateException("Levantamento não está em rascunho (status atual: " + l.getStatus() + ")");
        }
        if (!l.getEquipamentos().isEmpty()) {
            throw new IllegalStateException("Levantamento já possui equipamentos gerados");
        }

        List<Equipamento> equipamentos = new ArrayList<>();
        int seq = 1;

        // Servidores — divide entre Banco, Aplicação e Service Manager (se houver 3+)
        for (int i = 1; i <= l.getQtdServidores(); i++) {
            FuncaoEquipamento funcao = definirFuncaoServidor(i, l.getQtdServidores());
            equipamentos.add(criarEquipamento(l, CategoriaEquipamento.SERVIDOR, funcao, seq++));
        }

        // PDVs
        for (int i = 1; i <= l.getQtdPdvs(); i++) {
            equipamentos.add(criarEquipamento(l, CategoriaEquipamento.PDV, FuncaoEquipamento.PDV, seq++));
        }

        // Retaguardas
        for (int i = 1; i <= l.getQtdRetaguardas(); i++) {
            equipamentos.add(criarEquipamento(l, CategoriaEquipamento.RETAGUARDA, FuncaoEquipamento.RETAGUARDA, seq++));
        }

        // Consulta de preço
        if (Boolean.TRUE.equals(l.getConsultaPreco())) {
            for (int i = 1; i <= l.getQtdConsultaPreco(); i++) {
                equipamentos.add(criarEquipamento(l, CategoriaEquipamento.CONSULTA_PRECO, FuncaoEquipamento.CONSULTA_PRECO, seq++));
            }
        }

        equipamentoRepo.saveAll(equipamentos);
        l.getEquipamentos().addAll(equipamentos);
        l.setStatus(StatusLevantamento.EM_ANALISE);
        return levantamentoRepo.save(l);
    }

    @Transactional
    public Levantamento concluir(Long levantamentoId) {
        Levantamento l = buscar(levantamentoId);

        if (l.getStatus() == StatusLevantamento.CONCLUIDO) {
            return l;
        }
        if (l.getStatus() == StatusLevantamento.CANCELADO) {
            throw new IllegalStateException("Levantamento cancelado não pode ser concluído");
        }
        if (l.getEquipamentos().isEmpty()) {
            throw new IllegalStateException("Levantamento não possui equipamentos gerados");
        }

        List<Integer> pendentes = l.getEquipamentos().stream()
                .filter(e -> e.getAnalise() == null || e.getAnalise().getResultado() == StatusAnalise.PENDENTE)
                .map(Equipamento::getSequencia)
                .sorted()
                .toList();

        if (!pendentes.isEmpty()) {
            String sequencias = pendentes.stream().map(String::valueOf).collect(Collectors.joining(", "));
            String mensagem = pendentes.size() == 1
                    ? "1 equipamento ainda não analisado (sequência: " + sequencias + ")"
                    : pendentes.size() + " equipamentos ainda não analisados (sequências: " + sequencias + ")";
            throw new IllegalStateException(mensagem);
        }

        l.setStatus(StatusLevantamento.CONCLUIDO);
        return levantamentoRepo.save(l);
    }

    @Transactional
    public Levantamento cancelar(Long levantamentoId) {
        Levantamento l = buscar(levantamentoId);

        if (l.getStatus() == StatusLevantamento.CANCELADO) {
            return l;
        }
        if (l.getStatus() == StatusLevantamento.CONCLUIDO) {
            throw new IllegalStateException("Levantamento concluído não pode ser cancelado. Reabra-o primeiro.");
        }

        l.setStatus(StatusLevantamento.CANCELADO);
        return levantamentoRepo.save(l);
    }

    @Transactional
    public Levantamento reabrir(Long levantamentoId) {
        Levantamento l = buscar(levantamentoId);

        if (l.getStatus() != StatusLevantamento.CONCLUIDO) {
            throw new IllegalStateException("Só é possível reabrir levantamento concluído (status atual: "
                    + l.getStatus() + ")");
        }

        l.setStatus(StatusLevantamento.EM_ANALISE);
        return levantamentoRepo.save(l);
    }

    private FuncaoEquipamento definirFuncaoServidor(int indice, int total) {
        if (total == 1) return FuncaoEquipamento.APLICACAO; // servidor único = tudo
        if (total == 2) return indice == 1 ? FuncaoEquipamento.BANCO_DADOS : FuncaoEquipamento.APLICACAO;
        // 3 ou mais
        return switch (indice) {
            case 1 -> FuncaoEquipamento.BANCO_DADOS;
            case 2 -> FuncaoEquipamento.APLICACAO;
            case 3 -> FuncaoEquipamento.SERVICE_MANAGER;
            default -> FuncaoEquipamento.OUTRO;
        };
    }

    private Equipamento criarEquipamento(Levantamento l, CategoriaEquipamento cat, FuncaoEquipamento func, int seq) {
        Equipamento e = new Equipamento();
        e.setLevantamento(l);
        e.setCategoria(cat);
        e.setFuncao(func);
        e.setSequencia(seq);
        e.setStatus(StatusAnalise.PENDENTE);
        return e;
    }

    /**
     * Levantamentos do cliente, mais recentes primeiro. Sem clienteId, lista todos
     * (o controller restringe esse caso a ADMIN).
     */
    @Transactional(readOnly = true)
    public List<Levantamento> listarPorCliente(Long clienteId) {
        if (clienteId == null) {
            return levantamentoRepo.findAllOrderByCriadoEmDesc();
        }
        if (!clienteRepo.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente não encontrado");
        }
        return levantamentoRepo.findByClienteIdOrderByCriadoEmDesc(clienteId);
    }

    public List<EquipamentoResponse> listarEquipamentos(Long levantamentoId) {
        if (!levantamentoRepo.existsById(levantamentoId)) {
            throw new EntityNotFoundException("Levantamento não encontrado");
        }
        return equipamentoRepo.listarComQtdFotos(levantamentoId);
    }

    public Levantamento buscar(Long id) {
        return levantamentoRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Levantamento não encontrado"));
    }
}
