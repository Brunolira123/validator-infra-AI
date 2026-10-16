package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Fila do "Consultar agora". Um pedido aberto vale por {@code app.monitor.pedido-validade}: depois disso o agente
 * não o recebe mais e ele vira EXPIRADO (o agente provavelmente está offline).
 */
@Service
public class PedidoConsultaService {

    private static final List<StatusPedido> ABERTOS = List.of(StatusPedido.PENDENTE, StatusPedido.ENTREGUE);

    private final PedidoConsultaRepository repo;
    private final ClienteMonitoradoService clienteService;
    private final UsuarioLogadoService usuarioLogado;
    private final Duration validade;

    public PedidoConsultaService(PedidoConsultaRepository repo,
                                 ClienteMonitoradoService clienteService,
                                 UsuarioLogadoService usuarioLogado,
                                 @Value("${app.monitor.pedido-validade:PT5M}") Duration validade) {
        this.repo = repo;
        this.clienteService = clienteService;
        this.usuarioLogado = usuarioLogado;
        this.validade = validade;
    }

    /** Se já houver um pedido aberto para o cliente, devolve o mesmo (cliques repetidos não enfileiram vários). */
    @Transactional
    public PedidoResponse solicitar(Long clienteId) {
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        if (!cliente.getAtivo()) {
            throw new IllegalStateException("Cliente monitorado inativo");
        }

        PedidoConsulta pedido = repo.findFirstByClienteIdAndStatusInAndCriadoEmAfterOrderByIdAsc(
                        clienteId, ABERTOS, limiteValidade())
                .orElseGet(() -> {
                    PedidoConsulta novo = new PedidoConsulta();
                    novo.setCliente(cliente);
                    novo.setSolicitadoPor(usuarioLogado.usuario());
                    return repo.save(novo);
                });
        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse buscar(Long id) {
        PedidoConsulta pedido = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (pedido.aberto() && pedido.getCriadoEm().isBefore(limiteValidade())) {
            pedido.setStatus(StatusPedido.EXPIRADO);
        }
        return PedidoResponse.from(pedido);
    }

    /** Chamado no polling do agente: entrega o pedido pendente mais antigo, se houver. */
    @Transactional
    public Optional<PedidoConsulta> entregarProximo(Long clienteId) {
        return repo.findFirstByClienteIdAndStatusInAndCriadoEmAfterOrderByIdAsc(
                        clienteId, List.of(StatusPedido.PENDENTE), limiteValidade())
                .map(pedido -> {
                    pedido.setStatus(StatusPedido.ENTREGUE);
                    pedido.setEntregueEm(Instant.now());
                    return pedido;
                });
    }

    /** Vincula o resultado ao pedido. Aceita mesmo se já expirou: o resultado chegou, então vale. */
    @Transactional
    public void concluir(Long pedidoId, Long clienteId, Execucao execucao) {
        PedidoConsulta pedido = repo.findById(pedidoId)
                .filter(p -> p.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> new IllegalArgumentException("Pedido " + pedidoId + " não pertence a este agente"));
        pedido.setStatus(StatusPedido.CONCLUIDO);
        pedido.setConcluidoEm(Instant.now());
        pedido.setExecucao(execucao);
    }

    private Instant limiteValidade() {
        return Instant.now().minus(validade);
    }
}
