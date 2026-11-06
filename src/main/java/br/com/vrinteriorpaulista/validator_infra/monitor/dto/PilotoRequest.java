package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.NotNull;

public record PilotoRequest(
        @NotNull Boolean piloto
) {}
