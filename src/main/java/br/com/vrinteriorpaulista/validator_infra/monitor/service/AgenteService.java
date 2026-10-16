package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.OrigemExecucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ResultadoVerificacao;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * O que o agente faz no servidor: buscar pedidos (heartbeat) e entregar resultados.
 */
@Service
@Slf4j
public class AgenteService {

    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;
    private final ExecucaoRepository execucaoRepo;
    private final DiscordNotifier discord;

    public AgenteService(ClienteMonitoradoService clienteService,
                         PedidoConsultaService pedidoService,
                         ExecucaoRepository execucaoRepo,
                         DiscordNotifier discord) {
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
        this.execucaoRepo = execucaoRepo;
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
        execucao.setErro(req.erro() == null || req.erro().isBlank() ? null : req.erro().trim());
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
