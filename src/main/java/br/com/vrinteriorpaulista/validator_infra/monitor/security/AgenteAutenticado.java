package br.com.vrinteriorpaulista.validator_infra.monitor.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Principal das requisições do agente (token do cliente monitorado, não JWT de usuário).
 */
public record AgenteAutenticado(
        Long clienteId,
        String nome
) {
    public static final String ROLE = "AGENTE";

    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + ROLE));
    }
}
