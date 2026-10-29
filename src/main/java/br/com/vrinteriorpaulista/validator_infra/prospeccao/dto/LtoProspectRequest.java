package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Os mesmos campos do levantamento (LevantamentoRequest, sem o clienteId: o cliente vem do fechamento). */
public record LtoProspectRequest(
        @PositiveOrZero Integer qtdServidores,
        @PositiveOrZero Integer qtdPdvs,
        @PositiveOrZero Integer qtdRetaguardas,
        Boolean consultaPreco,
        @PositiveOrZero Integer qtdConsultaPreco,
        String outros
) {}
