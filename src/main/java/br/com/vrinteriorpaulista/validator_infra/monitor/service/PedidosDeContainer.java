package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Regras comuns aos pedidos sobre um container (LOG, START, RESTART): o container tem de estar na última foto
 * do ambiente, o agente tem de estar online e cada pedido só aceita uma resposta do agente dono.
 */
@Component
public class PedidosDeContainer {

    private final PedidoConsultaRepository pedidoRepo;
    private final AmbienteClienteRepository ambienteRepo;
    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;

    public PedidosDeContainer(PedidoConsultaRepository pedidoRepo,
                              AmbienteClienteRepository ambienteRepo,
                              ClienteMonitoradoService clienteService,
                              PedidoConsultaService pedidoService) {
        this.pedidoRepo = pedidoRepo;
        this.ambienteRepo = ambienteRepo;
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
    }

    /** 404 se o container não está na última foto do ambiente; 409 se o agente está offline. */
    public ContainerAmbiente exigirContainerComAgenteOnline(ClienteMonitorado cliente, String nome) {
        ContainerAmbiente container = ambienteRepo.findById(cliente.getId())
                .flatMap(a -> a.getContainers().stream().filter(c -> c.nome().equals(nome)).findFirst())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Container não encontrado no último ambiente enviado pelo agente"));
        if (!clienteService.online(cliente)) {
            throw new IllegalStateException("Agente do cliente está offline");
        }
        return container;
    }

    /** Pedido aberto do mesmo tipo para o mesmo container (clique repetido). */
    public Optional<PedidoConsulta> aberto(Long clienteId, TipoPedido tipo, String container) {
        return pedidoRepo.findAberto(clienteId, tipo, container, PedidoConsultaService.ABERTOS,
                pedidoService.limiteValidade());
    }

    /** Pedido do cliente e container da URL, já com a expiração avaliada; 404 se não for. */
    public PedidoConsulta doCliente(Long clienteId, String container, Long pedidoId, Predicate<TipoPedido> tipo,
                                    String naoEncontrado) {
        PedidoConsulta pedido = pedidoRepo.findById(pedidoId)
                .filter(p -> tipo.test(p.getTipo())
                        && p.getCliente().getId().equals(clienteId)
                        && container.equals(p.getContainer()))
                .orElseThrow(() -> new EntityNotFoundException(naoEncontrado));
        pedidoService.expirarSeVencido(pedido);
        return pedido;
    }

    /**
     * Pedido a ser respondido pelo agente: 400 se não for deste agente ou do tipo esperado, 409 se já foi
     * respondido. Aceita mesmo se já expirou (como a consulta): a resposta chegou, então vale.
     */
    public PedidoConsulta paraResponder(AgenteAutenticado agente, Long pedidoId, Predicate<TipoPedido> tipo,
                                        String descricao) {
        PedidoConsulta pedido = pedidoRepo.findById(pedidoId)
                .filter(p -> tipo.test(p.getTipo()) && p.getCliente().getId().equals(agente.clienteId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pedido " + pedidoId + " não é um pedido de " + descricao + " deste agente"));
        if (pedido.getStatus() == StatusPedido.CONCLUIDO || pedido.getStatus() == StatusPedido.ERRO) {
            throw new IllegalStateException("Pedido " + pedidoId + " já foi respondido");
        }
        return pedido;
    }
}
