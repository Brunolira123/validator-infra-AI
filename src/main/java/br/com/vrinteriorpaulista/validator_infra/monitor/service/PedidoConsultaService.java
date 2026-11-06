package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fila de pedidos ao agente ("Consultar agora" e log de container). Um pedido aberto vale por
 * {@code app.monitor.pedido-validade}: depois disso o agente não o recebe mais e ele vira EXPIRADO
 * (o agente provavelmente está offline).
 */
@Service
public class PedidoConsultaService {

    static final List<StatusPedido> ABERTOS = List.of(StatusPedido.PENDENTE, StatusPedido.ENTREGUE);
    private static final Pattern VERSAO = Pattern.compile("^\\s*v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private final PedidoConsultaRepository repo;
    private final ClienteMonitoradoService clienteService;
    private final UsuarioLogadoService usuarioLogado;
    private final Duration validade;
    private final String versaoMinimaLog;
    private final String versaoMinimaAcao;

    public PedidoConsultaService(PedidoConsultaRepository repo,
                                 ClienteMonitoradoService clienteService,
                                 UsuarioLogadoService usuarioLogado,
                                 @Value("${app.monitor.pedido-validade:PT5M}") Duration validade,
                                 @Value("${app.monitor.versao-minima-log:2.3.0}") String versaoMinimaLog,
                                 @Value("${app.monitor.versao-minima-acao:2.4.0}") String versaoMinimaAcao) {
        this.repo = repo;
        this.clienteService = clienteService;
        this.usuarioLogado = usuarioLogado;
        this.validade = validade;
        this.versaoMinimaLog = versaoMinimaLog;
        this.versaoMinimaAcao = versaoMinimaAcao;
    }

    /** Se já houver um pedido aberto para o cliente, devolve o mesmo (cliques repetidos não enfileiram vários). */
    @Transactional
    public PedidoResponse solicitar(Long clienteId) {
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        if (!cliente.getAtivo()) {
            throw new IllegalStateException("Cliente monitorado inativo");
        }

        PedidoConsulta pedido = repo.findAberto(clienteId, TipoPedido.CONSULTA, null, ABERTOS, limiteValidade())
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
                .filter(p -> p.getTipo() == TipoPedido.CONSULTA)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        expirarSeVencido(pedido);
        return PedidoResponse.from(pedido);
    }

    /** Marca EXPIRADO o pedido aberto além da validade (a expiração é avaliada na leitura). */
    void expirarSeVencido(PedidoConsulta pedido) {
        if (pedido.aberto() && pedido.getCriadoEm().isBefore(limiteValidade())) {
            pedido.setStatus(StatusPedido.EXPIRADO);
        }
    }

    /**
     * Chamado no polling do agente: entrega o pedido pendente mais antigo que a versão dele entende. Os demais
     * ficam na fila e expiram: LOG só a partir de {@code app.monitor.versao-minima-log}, START/RESTART só a
     * partir de {@code app.monitor.versao-minima-acao}.
     */
    @Transactional
    public Optional<PedidoConsulta> entregarProximo(Long clienteId, String versaoAgente) {
        List<TipoPedido> tipos = new ArrayList<>(List.of(TipoPedido.CONSULTA));
        if (aceitaLog(versaoAgente)) tipos.add(TipoPedido.LOG);
        if (aceitaAcao(versaoAgente)) tipos.addAll(List.of(TipoPedido.START, TipoPedido.RESTART));
        return repo.findFirstByClienteIdAndStatusAndTipoInAndCriadoEmAfterOrderByIdAsc(
                        clienteId, StatusPedido.PENDENTE, tipos, limiteValidade())
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
                .filter(p -> p.getCliente().getId().equals(clienteId) && p.getTipo() == TipoPedido.CONSULTA)
                .orElseThrow(() -> new IllegalArgumentException("Pedido " + pedidoId + " não pertence a este agente"));
        pedido.setStatus(StatusPedido.CONCLUIDO);
        pedido.setConcluidoEm(Instant.now());
        pedido.setExecucao(execucao);
    }

    /** Versão do agente (ex.: 2.3.0, 2.3.1-beta) >= app.monitor.versao-minima-log. Ausente ou ilegível: não. */
    public boolean aceitaLog(String versaoAgente) {
        return atende(versaoAgente, versaoMinimaLog);
    }

    /** Versão do agente >= app.monitor.versao-minima-acao (START/RESTART de container). */
    public boolean aceitaAcao(String versaoAgente) {
        return atende(versaoAgente, versaoMinimaAcao);
    }

    public String versaoMinimaAcao() {
        return versaoMinimaAcao;
    }

    private static boolean atende(String versaoAgente, String versaoMinima) {
        int[] versao = numeros(versaoAgente);
        int[] minima = numeros(versaoMinima);
        if (versao == null || minima == null) return false;
        for (int i = 0; i < 3; i++) {
            if (versao[i] != minima[i]) return versao[i] > minima[i];
        }
        return true;
    }

    private static int[] numeros(String versao) {
        if (versao == null) return null;
        Matcher m = VERSAO.matcher(versao);
        if (!m.find()) return null;
        int[] n = new int[3];
        for (int i = 0; i < 3; i++) {
            n[i] = m.group(i + 1) == null ? 0 : Integer.parseInt(m.group(i + 1));
        }
        return n;
    }

    Instant limiteValidade() {
        return Instant.now().minus(validade);
    }
}
