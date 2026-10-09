package br.com.vrinteriorpaulista.validator_infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica a requisição pelo header "Authorization: Bearer <token>".
 * Token ausente ou inválido não gera erro aqui: a requisição segue anônima e o SecurityConfig responde 401
 * onde a rota exigir autenticação.
 * <p>
 * Não é um @Component de propósito: se fosse, o Spring Boot também o registraria como filtro do servlet,
 * fora da cadeia do Spring Security.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIXO_BEARER)) {
            String token = header.substring(PREFIXO_BEARER.length()).trim();
            jwtService.lerAccessToken(token).ifPresent(usuario -> {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        usuario, null, usuario.authorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }

        chain.doFilter(request, response);
    }
}
