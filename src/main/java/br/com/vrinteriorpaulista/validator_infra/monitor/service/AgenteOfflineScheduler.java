package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Detecta agentes que pararam de reportar (máquina desligada, container parado, sem internet): a falha que
 * o modelo antigo, com o alerta saindo do próprio agente, não tinha como avisar.
 * Verifica na hora cheia (06:00, 07:00...), como a repetição das falhas no agente, e avisa a cada hora
 * enquanto o agente seguir offline; o "voltou" é enviado no próximo contato do agente (AgenteService).
 */
@Component
@Slf4j
public class AgenteOfflineScheduler {

    private final ClienteMonitoradoRepository repo;
    private final DiscordNotifier discord;
    private final Duration offlineApos;

    public AgenteOfflineScheduler(ClienteMonitoradoRepository repo,
                                  DiscordNotifier discord,
                                  @Value("${app.monitor.offline-apos:PT2H}") Duration offlineApos) {
        this.repo = repo;
        this.discord = discord;
        this.offlineApos = offlineApos;
    }

    @Scheduled(cron = "${app.monitor.cron-alerta-offline:0 0 * * * *}", zone = "${app.monitor.fuso:America/Sao_Paulo}")
    @Transactional
    public void verificar() {
        for (ClienteMonitorado c : repo.findByAtivoTrueAndUltimoContatoBefore(Instant.now().minus(offlineApos))) {
            log.warn("Agente do cliente monitorado {} ({}) sem contato desde {}", c.getId(), c.getNome(), c.getUltimoContato());
            c.setAlertaOfflineEnviado(true);
            discord.agenteOffline(c);
        }
    }
}
