package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Chamada crua a um modelo específico. Não implementa {@link VisionProvider}: quem atende a aplicação é o
 * {@link FallbackVisionProvider}, que escolhe o modelo e troca de modelo quando a IA está sobrecarregada.
 * O retry do Spring AI (spring.ai.retry.*) continua valendo dentro de cada chamada.
 */
@Component
public class OpenRouterVisionProvider {

    private final ChatClient chatClient;
    private final RespostaVisionParser parser;
    private final String promptTemplate;

    public OpenRouterVisionProvider(
            ChatClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("classpath:prompts/vision-extract.txt") Resource promptResource
    ) throws IOException {
        this.chatClient = builder.build();
        this.parser = new RespostaVisionParser(objectMapper);
        this.promptTemplate = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    public AnaliseEquipamentoDTO analisarImagem(byte[] imagemBytes, String mimeType, String modelo) {
        MimeType tipo = MimeTypeUtils.parseMimeType(mimeType);

        Media media = Media.builder()
                .mimeType(tipo)
                .data(imagemBytes)
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(promptTemplate)
                .media(media)
                .build();

        // Só o modelo é sobrescrito; temperatura e max-tokens vêm de spring.ai.openai.chat.options.*
        String resposta = chatClient.prompt()
                .options(ChatOptions.builder().model(modelo).build())
                .messages(userMessage)
                .call()
                .content();

        return parser.interpretar(resposta);
    }
}
