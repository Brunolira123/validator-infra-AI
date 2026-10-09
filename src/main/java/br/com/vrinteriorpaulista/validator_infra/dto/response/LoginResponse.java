package br.com.vrinteriorpaulista.validator_infra.dto.response;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEm,
        String refreshToken,
        UsuarioResponse usuario
) {}
