package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.ResultadoVisita;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.TemperaturaNegociacao;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** temperatura: obrigatória quando ATENDEU, proibida quando NAO_ATENDEU. latitude/longitude: posição do celular. */
public record VisitaProspectRequest(
        @NotNull ResultadoVisita resultado,
        TemperaturaNegociacao temperatura,
        @Size(max = 500) String observacao,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude
) {}
