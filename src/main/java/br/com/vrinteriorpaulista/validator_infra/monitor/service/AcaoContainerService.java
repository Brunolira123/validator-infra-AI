package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.AcaoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoAcaoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoCriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * START/RESTART de container pela tela de Suporte, pela mesma fila do log. O pedido é a auditoria: quem pediu,
 * cliente, container, ação, quando e o resultado; cada passo também sai numa linha INFO.
 */
@Service
@Slf4j
public class AcaoContainerService {

    private static final String SEM_DETALHE = "O agente não informou o motivo";

    private final PedidoConsultaRepository pedidoRepo;
    private final PedidosDeContainer pedidos;
    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;
    private final UsuarioLogadoService usuarioLogado;

    public AcaoContainerService(PedidoConsultaRepository pedidoRepo,
                                PedidosDeContainer pedidos,
                                ClienteMonitoradoService clienteService,
                                PedidoConsultaService pedidoService,
                                UsuarioLogadoService usuarioLogado) {
        this.pedidoRepo = pedidoRepo;
        this.pedidos = pedidos;
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
        this.usuarioLogado = usuarioLogado;
    }

    /**
     * 404 se o container não está na última foto; 409 se o agente está offline ou é anterior a
     * app.monitor.versao-minima-acao, ou se a ação não combina com o estado do container na última foto
     * (START só parado, RESTART só rodando). Clique repetido devolve o pedido aberto.
     */
    @Transactional
    public PedidoCriadoResponse solicitar(Long clienteId, String nome, TipoPedido tipo) {
        if (tipo == null || !tipo.acao()) {
            throw new IllegalArgumentException("tipo deve ser START ou RESTART");
        }
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        ContainerAmbiente container = pedidos.exigirContainerComAgenteOnline(cliente, nome);
        if (!pedidoService.aceitaAcao(cliente.getVersaoAgente())) {
            throw new IllegalStateException("Agente " + cliente.getVersaoAgente() + " não executa ações em container:"
                    + " é preciso a versão " + pedidoService.versaoMinimaAcao() + " ou mais nova");
        }
        if (tipo == TipoPedido.START && container.rodando()) {
            throw new IllegalStateException("Container " + nome + " já está rodando (última foto do ambiente)");
        }
        if (tipo == TipoPedido.RESTART && !container.rodando()) {
            throw new IllegalStateException("Container " + nome + " está parado (última foto do ambiente): use START");
        }

        PedidoConsulta pedido = pedidos.aberto(clienteId, tipo, nome)
                .orElseGet(() -> {
                    Usuario usuario = usuarioLogado.usuario();
                    PedidoConsulta novo = new PedidoConsulta();
                    novo.setCliente(cliente);
                    novo.setTipo(tipo);
                    novo.setContainer(nome);
                    novo.setSolicitadoPor(usuario);
                    pedidoRepo.save(novo);
                    log.info("Ação {} no container {} do cliente monitorado {} ({}) pedida por {} (pedido {})",
                            tipo, nome, cliente.getId(), cliente.getNome(), usuario.getLogin(), novo.getId());
                    return novo;
                });
        return new PedidoCriadoResponse(pedido.getId());
    }

    @Transactional
    public PedidoAcaoResponse buscar(Long clienteId, String container, Long pedidoId) {
        return PedidoAcaoResponse.from(pedidos.doCliente(clienteId, container, pedidoId,
                TipoPedido::acao, "Pedido de ação não encontrado"));
    }

    @Transactional
    public void receber(AgenteAutenticado agente, Long pedidoId, AcaoAgenteRequest req) {
        PedidoConsulta pedido = pedidos.paraResponder(agente, pedidoId, TipoPedido::acao, "ação");
        String erro = req.erro() == null || req.erro().isBlank() ? null : req.erro().trim();

        pedido.setStatus(req.ok() ? StatusPedido.CONCLUIDO : StatusPedido.ERRO);
        pedido.setConcluidoEm(Instant.now());
        pedido.setErro(req.ok() ? null : (erro != null ? erro : SEM_DETALHE));

        log.info("Ação {} no container {} do cliente monitorado {} (pedido {}, pedida por {}): {}",
                pedido.getTipo(), pedido.getContainer(), agente.clienteId(), pedidoId,
                pedido.getSolicitadoPor() != null ? pedido.getSolicitadoPor().getLogin() : "-",
                req.ok() ? "ok" : "ERRO: " + pedido.getErro());
    }
}
