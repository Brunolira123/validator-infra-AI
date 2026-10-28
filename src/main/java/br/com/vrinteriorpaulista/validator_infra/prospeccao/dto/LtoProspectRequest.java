package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** CNPJ do prospect + os mesmos campos do levantamento (LevantamentoRequest, sem o clienteId). */
public record LtoProspectRequest(
        @NotBlank String cnpj,
        @PositiveOrZero Integer qtdServidores,
        @PositiveOrZero Integer qtdPdvs,
        @PositiveOrZero Integer qtdRetaguardas,
        Boolean consultaPreco,
        @PositiveOrZero Integer qtdConsultaPreco,
        String outros
) {}
