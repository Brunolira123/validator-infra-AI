package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.LogAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogCriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import jakarta.persistence.EntityNotFoundException;
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
    private final AmbienteClienteRepository ambienteRepo;
    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;
    private final UsuarioLogadoService usuarioLogado;
    private final Duration retencao;

    public LogContainerService(PedidoConsultaRepository pedidoRepo,
                               AmbienteClienteRepository ambienteRepo,
                               ClienteMonitoradoService clienteService,
                               PedidoConsultaService pedidoService,
                               UsuarioLogadoService usuarioLogado,
                               @Value("${app.monitor.log-retencao:PT1H}") Duration retencao) {
        this.pedidoRepo = pedidoRepo;
        this.ambienteRepo = ambienteRepo;
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
    public PedidoLogCriadoResponse solicitar(Long clienteId, String container, int linhas) {
        if (linhas < 1 || linhas > MAX_LINHAS) {
            throw new IllegalArgumentException("linhas deve estar entre 1 e " + MAX_LINHAS);
        }
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        boolean existe = ambienteRepo.findById(clienteId)
                .map(a -> a.getContainers().stream().anyMatch(c -> c.nome().equals(container)))
                .orElse(false);
        if (!existe) {
            throw new EntityNotFoundException("Container não encontrado no último ambiente enviado pelo agente");
        }
        if (!clienteService.online(cliente)) {
            throw new IllegalStateException("Agente do cliente está offline");
        }
        if (!pedidoService.aceitaLog(cliente.getVersaoAgente())) {
            throw new IllegalStateException("Agente " + cliente.getVersaoAgente()
                    + " não envia log de container: atualize o agente");
        }

        PedidoConsulta pedido = pedidoRepo.findAberto(clienteId, TipoPedido.LOG, container,
                        PedidoConsultaService.ABERTOS, pedidoService.limiteValidade())
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
        return new PedidoLogCriadoResponse(pedido.getId());
    }

    @Transactional
    public PedidoLogResponse buscar(Long clienteId, String container, Long pedidoId) {
        PedidoConsulta pedido = pedidoRepo.findById(pedidoId)
                .filter(p -> p.getTipo() == TipoPedido.LOG
                        && p.getCliente().getId().equals(clienteId)
                        && p.getContainer().equals(container))
                .orElseThrow(() -> new EntityNotFoundException("Pedido de log não encontrado"));
        pedidoService.expirarSeVencido(pedido);
        return PedidoLogResponse.from(pedido);
    }

    /** Aceita mesmo se já expirou (como a consulta): o log chegou, então vale. Só uma resposta por pedido. */
    @Transactional
    public void receber(AgenteAutenticado agente, Long pedidoId, LogAgenteRequest req) {
        PedidoConsulta pedido = pedidoRepo.findById(pedidoId)
                .filter(p -> p.getTipo() == TipoPedido.LOG && p.getCliente().getId().equals(agente.clienteId()))
                .orElseThrow(() -> new IllegalArgumentException("Pedido " + pedidoId + " não é um pedido de log deste agente"));
        if (pedido.getStatus() == StatusPedido.CONCLUIDO || pedido.getStatus() == StatusPedido.ERRO) {
            throw new IllegalStateException("Pedido " + pedidoId + " já foi respondido");
        }

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
