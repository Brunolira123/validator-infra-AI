package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.Size;

public record DesativarClienteRequest(
        @Size(max = 300) String motivo
) {}
