package br.com.vrinteriorpaulista.validator_infra.dto.response;

public record TokenResponse(
        String token,
        String tipo,
        long expiraEm
) {}
