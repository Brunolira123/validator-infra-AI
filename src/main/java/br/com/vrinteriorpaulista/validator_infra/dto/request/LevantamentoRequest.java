package br.com.vrinteriorpaulista.validator_infra.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LevantamentoRequest(
        @NotNull Long clienteId,
        @NotNull Long usuarioId,
        @PositiveOrZero Integer qtdServidores,
        @PositiveOrZero Integer qtdPdvs,
        @PositiveOrZero Integer qtdRetaguardas,
        Boolean consultaPreco,
        @PositiveOrZero Integer qtdConsultaPreco,
        String outros
) {}
