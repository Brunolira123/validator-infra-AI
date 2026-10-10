package br.com.vrinteriorpaulista.validator_infra.controller.exception;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ErroResponse;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import br.com.vrinteriorpaulista.validator_infra.security.JsonSecurityErrorHandler;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String MENSAGEM_ERRO_INTERNO = "Erro interno. Tente novamente ou contate o suporte.";
    static final String MENSAGEM_IA_SOBRECARREGADA =
            "A IA está sobrecarregada no momento. Aguarde alguns segundos e tente novamente.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResponse> conflito(IllegalStateException ex, HttpServletRequest req) {
        return erro(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<ErroResponse> servicoIndisponivel(ServicoIndisponivelException ex, HttpServletRequest req) {
        log.warn("Serviço externo indisponível em {} {}: {}", req.getMethod(), req.getRequestURI(),
                ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        return erro(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<ErroResponse> iaIndisponivel(NonTransientAiException ex, HttpServletRequest req) {
        log.warn("Falha no provedor de IA em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de IA indisponível no momento", req, null);
    }

    /** Transitório = retries e fallback de modelo esgotados; o front exibe a mensagem como veio. */
    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<ErroResponse> iaSobrecarregada(TransientAiException ex, HttpServletRequest req) {
        log.warn("IA sobrecarregada em {} {}: {}", req.getMethod(), req.getRequestURI(),
                ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        return erro(HttpStatus.SERVICE_UNAVAILABLE, MENSAGEM_IA_SOBRECARREGADA, req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResponse> credenciaisInvalidas(BadCredentialsException ex, HttpServletRequest req) {
        return erro(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> naoAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return erro(HttpStatus.UNAUTHORIZED, JsonSecurityErrorHandler.MENSAGEM_NAO_AUTENTICADO, req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        return erro(HttpStatus.FORBIDDEN, JsonSecurityErrorHandler.MENSAGEM_ACESSO_NEGADO, req, null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(EntityNotFoundException ex, HttpServletRequest req) {
        return erro(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErroResponse.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroResponse.CampoInvalido(f.getField(), f.getDefaultMessage()))
                .toList();
        return erro(HttpStatus.BAD_REQUEST, "Dados inválidos", req, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return erro(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou mal formatado", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String mensagem = "Valor inválido para o parâmetro '" + ex.getName() + "': " + ex.getValue();
        return erro(HttpStatus.BAD_REQUEST, mensagem, req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponse> uploadMuitoGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return erro(HttpStatus.CONTENT_TOO_LARGE, "Foto muito grande. Máximo 20MB.", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> generico(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse springMvc) {
            return erro(springMvc.getStatusCode(), springMvc.getBody().getDetail(), req, null);
        }
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, MENSAGEM_ERRO_INTERNO, req, null);
    }

    private ResponseEntity<ErroResponse> erro(HttpStatusCode status,
                                              String mensagem,
                                              HttpServletRequest req,
                                              List<ErroResponse.CampoInvalido> campos) {
        ErroResponse body = new ErroResponse(mensagem, LocalDateTime.now(), req.getRequestURI(), campos);
        return ResponseEntity.status(status).body(body);
    }
}
