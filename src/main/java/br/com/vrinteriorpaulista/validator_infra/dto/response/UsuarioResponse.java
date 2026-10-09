package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        String perfil
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil().name());
    }
}
