package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;

import java.time.LocalDateTime;

/**
 * Usuário na tela de administração. Separado do {@link UsuarioResponse} do login para não mudar aquele contrato.
 */
public record UsuarioAdminResponse(
        Long id,
        String login,
        String nome,
        String email,
        String perfil,
        boolean ativo,
        LocalDateTime criadoEm
) {
    public static UsuarioAdminResponse from(Usuario u) {
        return new UsuarioAdminResponse(u.getId(), u.getLogin(), u.getNome(), u.getEmail(), u.getPerfil().name(),
                Boolean.TRUE.equals(u.getAtivo()), u.getCriadoEm());
    }
}
