package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteMonitoradoRequest(
        @NotBlank @Size(max = 150) String nome,
        /** Opcional; aceita formatado ou não. */
        String cnpj
) {}
