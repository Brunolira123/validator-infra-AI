package br.com.vrinteriorpaulista.validator_infra.controller.exception;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ErroResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite de multipart é aplicado pelo Tomcat ao fazer o parse; o MockMvc não passa por ele,
 * então o mapeamento da exceção é testado direto no handler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveRetornar413QuandoUploadExcederOLimite() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/equipamentos/1/fotos");

        ResponseEntity<ErroResponse> resposta = handler.uploadMuitoGrande(
                new MaxUploadSizeExceededException(20L * 1024 * 1024), req);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(resposta.getBody().mensagem()).isEqualTo("Foto muito grande. Máximo 20MB.");
        assertThat(resposta.getBody().path()).isEqualTo("/api/equipamentos/1/fotos");
    }
}
