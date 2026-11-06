package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.LogAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoCriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Log de container sob demanda (tela de Suporte). Vai pela mesma fila do "Consultar agora": o servidor não
 * alcança o cliente, o agente busca o pedido no polling e devolve o texto.
 * <p>
 * O texto do log pode ter dados do cliente: nunca vai para o log do servidor e é apagado do banco depois de
 * {@code app.monitor.log-retencao}. O pedido em si (quem, cliente, container, quando) fica como auditoria.
 */
@Service
@Slf4j
public class LogContainerService {

    public static final int MAX_LINHAS = 500;

    private final PedidoConsultaRepository pedidoRepo;
    private final PedidosDeContainer pedidos;
    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;
    private final UsuarioLogadoService usuarioLogado;
    private final Duration retencao;

    public LogContainerService(PedidoConsultaRepository pedidoRepo,
                               PedidosDeContainer pedidos,
                               ClienteMonitoradoService clienteService,
                               PedidoConsultaService pedidoService,
                               UsuarioLogadoService usuarioLogado,
                               @Value("${app.monitor.log-retencao:PT1H}") Duration retencao) {
        this.pedidoRepo = pedidoRepo;
        this.pedidos = pedidos;
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
        this.usuarioLogado = usuarioLogado;
        this.retencao = retencao;
    }

    /**
     * 404 se o container não está na última foto do ambiente; 409 se o agente está offline ou é antigo demais
     * para atender log. Clique repetido no mesmo container devolve o pedido aberto.
     */
    @Transactional
    public PedidoCriadoResponse solicitar(Long clienteId, String container, int linhas) {
        if (linhas < 1 || linhas > MAX_LINHAS) {
            throw new IllegalArgumentException("linhas deve estar entre 1 e " + MAX_LINHAS);
        }
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        pedidos.exigirContainerComAgenteOnline(cliente, container);
        if (!pedidoService.aceitaLog(cliente.getVersaoAgente())) {
            throw new IllegalStateException("Agente " + cliente.getVersaoAgente()
                    + " não envia log de container: atualize o agente");
        }

        PedidoConsulta pedido = pedidos.aberto(clienteId, TipoPedido.LOG, container)
                .orElseGet(() -> {
                    Usuario usuario = usuarioLogado.usuario();
                    PedidoConsulta novo = new PedidoConsulta();
                    novo.setCliente(cliente);
                    novo.setTipo(TipoPedido.LOG);
                    novo.setContainer(container);
                    novo.setLinhas(linhas);
                    novo.setSolicitadoPor(usuario);
                    pedidoRepo.save(novo);
                    log.info("Log do container {} do cliente monitorado {} ({}) pedido por {} (pedido {}, {} linhas)",
                            container, cliente.getId(), cliente.getNome(), usuario.getLogin(), novo.getId(), linhas);
                    return novo;
                });
        return new PedidoCriadoResponse(pedido.getId());
    }

    @Transactional
    public PedidoLogResponse buscar(Long clienteId, String container, Long pedidoId) {
        return PedidoLogResponse.from(pedidos.doCliente(clienteId, container, pedidoId,
                t -> t == TipoPedido.LOG, "Pedido de log não encontrado"));
    }

    @Transactional
    public void receber(AgenteAutenticado agente, Long pedidoId, LogAgenteRequest req) {
        PedidoConsulta pedido = pedidos.paraResponder(agente, pedidoId, t -> t == TipoPedido.LOG, "log");

        String erro = req.erro() == null || req.erro().isBlank() ? null : req.erro().trim();
        if (erro == null) {
            if (req.texto() == null) {
                throw new IllegalArgumentException("Informe o texto ou o erro");
            }
            int bytes = req.texto().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > LogAgenteRequest.MAX_BYTES) {
                throw new IllegalArgumentException("Texto do log com " + bytes + " bytes: o limite é "
                        + LogAgenteRequest.MAX_BYTES);
            }
        }

        pedido.setStatus(erro == null ? StatusPedido.CONCLUIDO : StatusPedido.ERRO);
        pedido.setConcluidoEm(Instant.now());
        pedido.setErro(erro);
        pedido.setTexto(erro == null ? req.texto() : null);
        pedido.setTruncado(erro == null && Boolean.TRUE.equals(req.truncado()));

        // Só metadados: o texto nunca vai para o log do servidor
        log.info("Log do container {} do cliente monitorado {} recebido (pedido {}): {}", pedido.getContainer(),
                agente.clienteId(), pedidoId, erro == null
                        ? req.texto().length() + " caracteres" + (pedido.getTruncado() ? ", truncado" : "")
                        : "ERRO");
    }

    @Scheduled(fixedDelayString = "${app.monitor.limpeza-log-intervalo:PT5M}")
    @Transactional
    public void limparTextos() {
        Instant agora = Instant.now();
        int apagados = pedidoRepo.apagarTextosDeLog(agora.minus(retencao), agora);
        if (apagados > 0) {
            log.info("Texto de {} log(s) de container apagado(s) após {}", apagados, retencao);
        }
    }
}
