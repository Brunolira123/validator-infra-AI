package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** ativo null = true. */
public record CategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal horasPadrao,
        Boolean ativo
) {}
