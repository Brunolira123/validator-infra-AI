package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Resultado de um START/RESTART. {@code ok=false} vira ERRO com o {@code erro} informado.
 */
public record AcaoAgenteRequest(
        @NotNull Boolean ok,
        @Size(max = 500) String erro
) {}
