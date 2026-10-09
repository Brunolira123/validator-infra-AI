package br.com.vrinteriorpaulista.validator_infra.security;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Respostas 401/403 geradas pela cadeia de filtros do Spring Security, no mesmo formato do ErroResponse.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String MENSAGEM_NAO_AUTENTICADO = "Autenticação necessária. Envie um token válido no header Authorization.";
    public static final String MENSAGEM_ACESSO_NEGADO = "Acesso negado para o seu perfil";

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        escrever(response, request, HttpStatus.UNAUTHORIZED, MENSAGEM_NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escrever(response, request, HttpStatus.FORBIDDEN, MENSAGEM_ACESSO_NEGADO);
    }

    private void escrever(HttpServletResponse response,
                          HttpServletRequest request,
                          HttpStatus status,
                          String mensagem) throws IOException {
        ErroResponse body = new ErroResponse(mensagem, LocalDateTime.now(), request.getRequestURI(), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
