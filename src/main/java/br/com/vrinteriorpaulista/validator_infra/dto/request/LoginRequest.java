package br.com.vrinteriorpaulista.validator_infra.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String login,
        @NotBlank String senha
) {}
