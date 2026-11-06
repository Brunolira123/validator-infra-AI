package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAtualizacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoAlvoLoteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AtualizacaoAgente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusAtualizacao;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AtualizacaoAgenteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioLogadoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Atualização do agente pelo portal. O vrip-updater do cliente pergunta qual versão instalar e informa o
 * resultado; o Suporte define a versão alvo por cliente ou em lote.
 * <p>
 * Uma versão que falhou (FALHOU/ROLLBACK) não é oferecida de novo ao mesmo cliente: o updater só tenta quando
 * o alvo muda para outra versão.
 */
@Service
@Slf4j
public class AtualizacaoAgenteService {

    private final ClienteMonitoradoService clienteService;
    private final ClienteMonitoradoRepository clienteRepo;
    private final AtualizacaoAgenteRepository atualizacaoRepo;
    private final UsuarioLogadoService usuarioLogado;

    public AtualizacaoAgenteService(ClienteMonitoradoService clienteService,
                                    ClienteMonitoradoRepository clienteRepo,
                                    AtualizacaoAgenteRepository atualizacaoRepo,
                                    UsuarioLogadoService usuarioLogado) {
        this.clienteService = clienteService;
        this.clienteRepo = clienteRepo;
        this.atualizacaoRepo = atualizacaoRepo;
        this.usuarioLogado = usuarioLogado;
    }

    // ---- vrip-updater ----

    /**
     * Versão a instalar, ou vazio se não há alvo, se o agente já está nela ou se ela já falhou neste cliente.
     * Registra o contato do updater (não conta como contato do agente).
     */
    @Transactional
    public Optional<String> versaoPendente(AgenteAutenticado agente, String versaoUpdater) {
        ClienteMonitorado cliente = registrarContatoUpdater(agente, versaoUpdater);
        String alvo = cliente.getVersaoAlvoAgente();
        if (alvo == null || alvo.equals(cliente.getVersaoAlvoFalhou()) || alvo.equals(cliente.getVersaoAgente())) {
            return Optional.empty();
        }
        return Optional.of(alvo);
    }

    @Transactional
    public void receberResultado(AgenteAutenticado agente, String versaoUpdater, ResultadoAtualizacaoRequest req) {
        ClienteMonitorado cliente = registrarContatoUpdater(agente, versaoUpdater);
        String alvo = req.versaoAlvo().trim();

        AtualizacaoAgente a = new AtualizacaoAgente();
        a.setCliente(cliente);
        a.setVersaoAnterior(vazioComoNulo(req.versaoAnterior()));
        a.setVersaoAlvo(alvo);
        a.setStatus(req.status());
        a.setDetalhe(vazioComoNulo(req.detalhe()));
        a.setEm(Instant.now());
        atualizacaoRepo.save(a);

        if (req.status() == StatusAtualizacao.ATUALIZADO) {
            // Só limpa se for o alvo atual: o Suporte pode ter trocado o alvo enquanto o updater trabalhava
            if (alvo.equals(cliente.getVersaoAlvoAgente())) {
                cliente.setVersaoAlvoAgente(null);
            }
            if (alvo.equals(cliente.getVersaoAlvoFalhou())) {
                cliente.setVersaoAlvoFalhou(null);
            }
            log.info("Agente do cliente monitorado {} ({}) atualizado de {} para {}",
                    cliente.getId(), cliente.getNome(), a.getVersaoAnterior(), alvo);
        } else {
            cliente.setVersaoAlvoFalhou(alvo);
            log.warn("Atualização do agente do cliente monitorado {} ({}) de {} para {}: {} ({})",
                    cliente.getId(), cliente.getNome(), a.getVersaoAnterior(), alvo, req.status(), a.getDetalhe());
        }
    }

    // ---- Suporte ----

    /** versao null cancela a atualização pendente. */
    @Transactional
    public void definirVersaoAlvo(Long clienteId, String versao) {
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        cliente.setVersaoAlvoAgente(vazioComoNulo(versao));
        log.info("Versão alvo do agente do cliente monitorado {} ({}) definida como {} por {}",
                cliente.getId(), cliente.getNome(), cliente.getVersaoAlvoAgente(), login());
    }

    /** Marca a versão como alvo dos clientes ativos do escopo, exceto os que já rodam essa versão. */
    @Transactional
    public int definirVersaoAlvoEmLote(VersaoAlvoLoteRequest req) {
        String versao = req.versao().trim();
        List<ClienteMonitorado> clientes = switch (req.escopo()) {
            case PILOTO -> clienteRepo.findByAtivoTrueAndPilotoTrue();
            case TODOS -> clienteRepo.findByAtivoTrueOrderByNomeAsc();
            case IDS -> {
                if (req.ids() == null || req.ids().isEmpty()) {
                    throw new IllegalArgumentException("Informe os ids no escopo IDS");
                }
                yield clienteRepo.findAllById(req.ids().stream().filter(Objects::nonNull).toList()).stream()
                        .filter(ClienteMonitorado::getAtivo)
                        .toList();
            }
        };

        List<ClienteMonitorado> marcados = clientes.stream()
                .filter(c -> !versao.equals(c.getVersaoAgente()))
                .toList();
        marcados.forEach(c -> c.setVersaoAlvoAgente(versao));

        log.info("Versão alvo {} do agente definida em lote ({}) por {} para {} cliente(s): {}",
                versao, req.escopo(), login(), marcados.size(),
                marcados.stream().map(ClienteMonitorado::getId).toList());
        return marcados.size();
    }

    @Transactional
    public void definirPiloto(Long clienteId, boolean piloto) {
        ClienteMonitorado cliente = clienteService.buscar(clienteId);
        cliente.setPiloto(piloto);
        log.info("Cliente monitorado {} ({}) {} do piloto por {}", cliente.getId(), cliente.getNome(),
                piloto ? "incluído no" : "retirado", login());
    }

    private ClienteMonitorado registrarContatoUpdater(AgenteAutenticado agente, String versaoUpdater) {
        ClienteMonitorado cliente = clienteService.buscar(agente.clienteId());
        cliente.setUpdaterUltimoContato(Instant.now());
        if (versaoUpdater != null && !versaoUpdater.isBlank()) {
            String v = versaoUpdater.trim();
            cliente.setUpdaterVersao(v.length() > 30 ? v.substring(0, 30) : v);
        }
        return cliente;
    }

    private String login() {
        return usuarioLogado.usuario().getLogin();
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
