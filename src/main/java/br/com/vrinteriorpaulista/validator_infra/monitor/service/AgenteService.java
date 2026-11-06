package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.AmbienteAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.NfceNaoTransmitida;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.OrigemExecucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ResultadoVerificacao;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * O que o agente faz no servidor: buscar pedidos (heartbeat), entregar resultados e a foto do ambiente.
 */
@Service
@Slf4j
public class AgenteService {

    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;
    private final ExecucaoRepository execucaoRepo;
    private final AmbienteClienteRepository ambienteRepo;
    private final DiscordNotifier discord;

    public AgenteService(ClienteMonitoradoService clienteService,
                         PedidoConsultaService pedidoService,
                         ExecucaoRepository execucaoRepo,
                         AmbienteClienteRepository ambienteRepo,
                         DiscordNotifier discord) {
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
        this.execucaoRepo = execucaoRepo;
        this.ambienteRepo = ambienteRepo;
        this.discord = discord;
    }

    @Transactional
    public Optional<PedidoConsulta> buscarPedido(AgenteAutenticado agente, String versaoAgente) {
        registrarContato(agente, versaoAgente);
        return pedidoService.entregarProximo(agente.clienteId());
    }

    @Transactional
    public Execucao receberResultado(AgenteAutenticado agente, ResultadoAgenteRequest req) {
        ClienteMonitorado cliente = registrarContato(agente, req.versaoAgente());

        Execucao execucao = new Execucao();
        execucao.setCliente(cliente);
        execucao.setOrigem(req.origem());
        execucao.setExecutadoEm(req.executadoEm().toInstant());
        execucao.setRecebidoEm(Instant.now());
        execucao.setVersaoAgente(req.versaoAgente());
        execucao.setErro(vazioComoNulo(req.erro()));
        execucao.setVersaoBanco(vazioComoNulo(req.versaoBanco()));
        execucao.setEncerramentoAutomatico(req.encerramentoAutomatico());
        execucao.setConsistenciaAutomatica(req.consistenciaAutomatica());
        for (ResultadoAgenteRequest.Loja loja : req.lojas()) {
            for (ResultadoAgenteRequest.Verificacao v : loja.verificacoes()) {
                ResultadoVerificacao r = new ResultadoVerificacao();
                r.setLojaId(loja.lojaId());
                r.setLojaCnpj(loja.cnpj());
                r.setLojaDescricao(loja.descricao());
                r.setTipo(v.tipo().trim().toUpperCase());
                r.setOk(v.ok());
                r.setReferencia(v.referencia());
                r.setDetalhe(v.detalhe());
                execucao.adicionar(r);
            }
            if (loja.nfceNaoTransmitidas() != null) {
                for (ResultadoAgenteRequest.NfceNaoTransmitida n : loja.nfceNaoTransmitidas()) {
                    NfceNaoTransmitida nfce = new NfceNaoTransmitida();
                    nfce.setLojaId(loja.lojaId());
                    nfce.setContingencia(n.contingencia());
                    nfce.setMotivoRejeicao(vazioComoNulo(n.motivoRejeicao()));
                    execucao.adicionar(nfce);
                }
            }
        }
        execucao.setOk(execucao.getErro() == null
                && execucao.getResultados().stream().allMatch(ResultadoVerificacao::getOk));
        execucaoRepo.save(execucao);

        if (req.pedidoId() != null) {
            pedidoService.concluir(req.pedidoId(), cliente.getId(), execucao);
        }

        log.info("Execução {} do cliente monitorado {} ({}): {}, {} loja(s) com falha",
                execucao.getId(), cliente.getId(), req.origem(), execucao.getOk() ? "ok" : "FALHA",
                execucao.lojasComFalha());

        // Sob demanda quem pediu já está olhando a tela; o Discord recebe só a rotina agendada (como hoje)
        if (req.origem() == OrigemExecucao.AGENDADO) {
            discord.resultado(cliente, execucao);
        }
        return execucao;
    }

    /** Substitui a foto anterior: só a última interessa. */
    @Transactional
    public AmbienteCliente receberAmbiente(AgenteAutenticado agente, AmbienteAgenteRequest req) {
        ClienteMonitorado cliente = registrarContato(agente, req.versaoAgente());

        AmbienteCliente ambiente = ambienteRepo.findById(cliente.getId()).orElseGet(() -> {
            AmbienteCliente novo = new AmbienteCliente();
            novo.setCliente(cliente);
            return novo;
        });
        ambiente.setColetadoEm(req.coletadoEm().toInstant());
        ambiente.setRecebidoEm(Instant.now());
        ambiente.setDockerDisponivel(req.dockerDisponivel());
        ambiente.setErro(vazioComoNulo(req.erro()));
        ambiente.setContainers(req.containers().stream()
                .map(c -> new ContainerAmbiente(c.nome().trim(), vazioComoNulo(c.versao()), c.rodando()))
                .toList());
        ambienteRepo.save(ambiente);

        log.debug("Ambiente do cliente monitorado {}: docker {}, {} container(s)", cliente.getId(),
                req.dockerDisponivel() ? "ok" : "indisponível", req.containers().size());
        return ambiente;
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private ClienteMonitorado registrarContato(AgenteAutenticado agente, String versaoAgente) {
        ClienteMonitorado cliente = clienteService.buscar(agente.clienteId());
        cliente.setUltimoContato(Instant.now());
        if (versaoAgente != null && !versaoAgente.isBlank()) {
            cliente.setVersaoAgente(versaoAgente.trim());
        }
        if (cliente.getAlertaOfflineEnviado()) {
            cliente.setAlertaOfflineEnviado(false);
            discord.agenteVoltou(cliente);
        }
        return cliente;
    }
}
