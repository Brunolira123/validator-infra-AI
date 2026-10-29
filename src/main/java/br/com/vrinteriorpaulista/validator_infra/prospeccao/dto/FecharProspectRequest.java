package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** dataFechamento: padrão hoje. observacao substitui a do prospect quando informada. */
public record FecharProspectRequest(
        @NotBlank String cnpj,
        @PastOrPresent LocalDate dataFechamento,
        @Size(max = 500) String observacao
) {}
