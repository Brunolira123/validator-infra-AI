package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.MotivoPerda;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PerderProspectRequest(
        @NotNull MotivoPerda motivo,
        @Size(max = 500) String observacao
) {}
