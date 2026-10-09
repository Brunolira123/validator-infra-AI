package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class OpenRouterVisionProvider implements VisionProvider {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public OpenRouterVisionProvider(
            ChatClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("classpath:prompts/vision-extract.txt") Resource promptResource
    ) throws IOException {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
        this.promptTemplate = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public AnaliseEquipamentoDTO analisarImagem(byte[] imagemBytes, String mimeType) {
        MimeType tipo = MimeTypeUtils.parseMimeType(mimeType);

        Media media = Media.builder()
                .mimeType(tipo)
                .data(imagemBytes)
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(promptTemplate)
                .media(media)
                .build();

        String resposta = chatClient.prompt()
                .messages(userMessage)
                .call()
                .content();

        try {
            String jsonLimpo = extrairJson(resposta);
            return objectMapper.readValue(jsonLimpo, AnaliseEquipamentoDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao desserializar resposta da IA: " + resposta, e);
        }
    }

    /**
     * Remove markdown/cercas que a IA às vezes coloca em volta do JSON.
     */
    private String extrairJson(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            throw new RuntimeException("Resposta da IA vazia");
        }

        String limpo = resposta.trim();

        // Remove cercas markdown ```json ... ```
        if (limpo.startsWith("```")) {
            limpo = limpo.replaceFirst("^```(?:json)?\\s*", "");
            limpo = limpo.replaceFirst("\\s*```$", "");
            limpo = limpo.trim();
        }

        // Se ainda não começa com {, procura o primeiro { e o último }
        int primeiroChave = limpo.indexOf('{');
        int ultimoChave = limpo.lastIndexOf('}');

        if (primeiroChave == -1 || ultimoChave == -1 || ultimoChave < primeiroChave) {
            throw new RuntimeException("Nenhum JSON encontrado na resposta: " + resposta);
        }

        return limpo.substring(primeiroChave, ultimoChave + 1);
    }
}
