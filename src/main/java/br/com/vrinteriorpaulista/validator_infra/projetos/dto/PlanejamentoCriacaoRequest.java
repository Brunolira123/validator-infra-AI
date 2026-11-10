package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Planejamento avulso (fora do LTO). Nasce em RASCUNHO. */
public record PlanejamentoCriacaoRequest(
        @NotNull Long clienteId,
        Long levantamentoId,
        @NotBlank @Size(max = 200) String nome,
        LocalDate kickoff,
        @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal horasContratadas,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal custoHoraServico,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal custoHoraViagem,
        Long responsavelId,
        @Size(max = 1000) String observacao
) {}
