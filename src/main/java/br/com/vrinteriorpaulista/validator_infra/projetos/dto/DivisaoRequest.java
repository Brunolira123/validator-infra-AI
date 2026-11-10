package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DivisaoRequest(
        @NotNull Long usuarioId,
        @NotNull @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal horas
) {}
