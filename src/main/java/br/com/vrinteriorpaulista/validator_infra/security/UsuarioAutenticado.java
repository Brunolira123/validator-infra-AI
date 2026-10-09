package br.com.vrinteriorpaulista.validator_infra.security;

import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Principal colocado no SecurityContext a partir das claims do access token.
 */
public record UsuarioAutenticado(
        Long id,
        String login,
        Perfil perfil
) {
    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
