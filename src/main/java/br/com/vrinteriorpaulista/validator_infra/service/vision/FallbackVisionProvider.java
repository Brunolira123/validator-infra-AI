package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tenta os modelos em ordem. Cada modelo tem {@code tentativasPorModelo} chamadas; se todas falharem com erro
 * transitório ({@link TransientAiException}: 5xx como o 503 "high demand", e 429 via spring.ai.retry.on-http-codes), passa para o próximo. Erro não transitório (chave inválida,
 * requisição malformada) sobe na hora: trocar de modelo não resolveria.
 */
@Component
@Slf4j
public class FallbackVisionProvider implements VisionProvider {

    private final OpenRouterVisionProvider cliente;
    private final List<String> modelos;
    private final int tentativasPorModelo;

    public FallbackVisionProvider(
            OpenRouterVisionProvider cliente,
            @Value("${app.ai.modelos:gemini-3.8-flash,gemini-2.0-flash,gemini-2.5-flash}") List<String> modelos,
            @Value("${app.ai.tentativas-por-modelo:2}") int tentativasPorModelo
    ) {
        if (modelos.isEmpty()) {
            throw new IllegalArgumentException("app.ai.modelos precisa de pelo menos um modelo");
        }
        if (tentativasPorModelo < 1) {
            throw new IllegalArgumentException("app.ai.tentativas-por-modelo precisa ser >= 1");
        }
        this.cliente = cliente;
        this.modelos = List.copyOf(modelos);
        this.tentativasPorModelo = tentativasPorModelo;
    }

    @Override
    public AnaliseEquipamentoDTO analisarImagem(byte[] imagemBytes, String mimeType) {
        TransientAiException ultimaFalha = null;

        for (int i = 0; i < modelos.size(); i++) {
            String modelo = modelos.get(i);
            for (int tentativa = 1; tentativa <= tentativasPorModelo; tentativa++) {
                try {
                    return cliente.analisarImagem(imagemBytes, mimeType, modelo);
                } catch (TransientAiException e) {
                    ultimaFalha = e;
                    log.debug("{} falhou (tentativa {}/{}): {}", modelo, tentativa, tentativasPorModelo, e.getMessage());
                }
            }

            String motivo = resumo(ultimaFalha);
            if (i + 1 < modelos.size()) {
                log.warn("{} falhou {}x com {}, tentando {}", modelo, tentativasPorModelo, motivo, modelos.get(i + 1));
            } else {
                log.warn("{} falhou {}x com {}; nenhum modelo restante", modelo, tentativasPorModelo, motivo);
            }
        }

        throw new TransientAiException("Todos os modelos de IA falharam: " + String.join(", ", modelos), ultimaFalha);
    }

    /** O Spring AI monta a mensagem como "HTTP 503 - {corpo}"; no log basta o código. */
    private static String resumo(TransientAiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.startsWith("HTTP ") && msg.length() >= 8) {
            return msg.substring(5, 8);
        }
        return e.getClass().getSimpleName();
    }
}
