package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** horas entre 0 e a jornada (app.projetos.horas-dia). */
public record DeslocamentoRequest(
        @NotNull Long usuarioId,
        @NotNull LocalDate data,
        @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal horas,
        @Size(max = 200) String descricao
) {}
