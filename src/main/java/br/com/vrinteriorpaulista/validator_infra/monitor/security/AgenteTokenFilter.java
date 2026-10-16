package br.com.vrinteriorpaulista.validator_infra.monitor.security;

import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica o agente pelo header "Authorization: Agente <token>".
 * Só atua em /api/monitor/agente/**: fora dali o token do agente não autentica nada (nem os GETs do comercial,
 * que só exigem "estar autenticado"). Token ausente ou inválido segue anônimo e o SecurityConfig responde 401.
 * <p>
 * Não é um @Component pelo mesmo motivo do JwtAuthenticationFilter.
 */
public class AgenteTokenFilter extends OncePerRequestFilter {

    public static final String CAMINHO = "/api/monitor/agente/";
    private static final String PREFIXO = "Agente ";

    private final ClienteMonitoradoRepository clienteRepo;

    public AgenteTokenFilter(ClienteMonitoradoRepository clienteRepo) {
        this.clienteRepo = clienteRepo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(CAMINHO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(PREFIXO)) {
            String token = header.substring(PREFIXO.length()).trim();
            clienteRepo.findByTokenHashAndAtivoTrue(TokenAgente.hash(token)).ifPresent(cliente -> {
                var agente = new AgenteAutenticado(cliente.getId(), cliente.getNome());
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        agente, null, agente.authorities());

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }

        chain.doFilter(request, response);
    }
}
