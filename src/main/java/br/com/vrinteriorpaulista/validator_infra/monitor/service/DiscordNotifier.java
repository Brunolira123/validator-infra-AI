package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ResultadoVerificacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alertas do monitoramento no Discord. Webhook vazio = alertas desligados (dev e testes).
 * Falha no Discord só gera log: nunca derruba o recebimento do resultado do agente.
 */
@Component
@Slf4j
public class DiscordNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Limite de caracteres de uma mensagem do Discord. */
    private static final int LIMITE_MENSAGEM = 2000;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordNotifier(RestClient.Builder builder,
                           @Value("${app.monitor.discord-webhook-url:}") String webhookUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = builder.requestFactory(requestFactory).build();
        this.webhookUrl = webhookUrl;
    }

    public void resultado(ClienteMonitorado cliente, Execucao execucao) {
        long total = execucao.totalLojas();
        if (execucao.getErro() != null) {
            enviar("❌ **%s**: o agente não conseguiu consultar o banco: %s".formatted(cliente.getNome(), execucao.getErro()));
            return;
        }
        if (execucao.getOk()) {
            enviar("✅ **%s**: todas as verificações ok (%d loja(s)).".formatted(cliente.getNome(), total));
            return;
        }

        String falhas = execucao.getResultados().stream()
                .filter(r -> !r.getOk())
                .map(this::linhaFalha)
                .collect(Collectors.joining("\n"));
        enviar("⚠️ **%s**: falha em %d de %d loja(s)\n%s"
                .formatted(cliente.getNome(), execucao.lojasComFalha(), total, falhas));
    }

    public void agenteOffline(ClienteMonitorado cliente) {
        long horas = Duration.between(cliente.getUltimoContato(), Instant.now()).toHours();
        enviar("🔌 **%s**: agente offline, sem contato desde %s (há %dh)."
                .formatted(cliente.getNome(), DATA_HORA.format(cliente.getUltimoContato()), horas));
    }

    public void agenteVoltou(ClienteMonitorado cliente) {
        enviar("🔌 **%s**: agente voltou a responder.".formatted(cliente.getNome()));
    }

    private String linhaFalha(ResultadoVerificacao r) {
        String loja = r.getLojaDescricao() != null ? r.getLojaDescricao() : "Loja " + r.getLojaId();
        String referencia = r.getReferencia() != null ? " (" + DATA.format(r.getReferencia()) + ")" : "";
        String detalhe = r.getDetalhe() != null ? ": " + r.getDetalhe() : "";
        return "• %s: %s ❌%s%s".formatted(loja, r.getTipo(), referencia, detalhe);
    }

    private void enviar(String mensagem) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Discord desligado (app.monitor.discord-webhook-url vazio): {}", mensagem);
            return;
        }
        String conteudo = mensagem.length() > LIMITE_MENSAGEM
                ? mensagem.substring(0, LIMITE_MENSAGEM - 1) + "…"
                : mensagem;
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", conteudo))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Falha ao enviar alerta para o Discord em {}: {}", Instant.now(), e.getMessage());
        }
    }
}
