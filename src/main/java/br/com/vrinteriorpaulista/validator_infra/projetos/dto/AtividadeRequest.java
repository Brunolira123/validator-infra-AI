package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * horas null com categoria = horas padrão da categoria. contatoClienteId só com doCliente.
 * ordem null = no fim (ao criar) ou mantém (ao editar). Distribuição e dependências têm endpoints próprios.
 */
public record AtividadeRequest(
        @NotBlank @Size(max = 200) String nome,
        Long paiId,
        Long categoriaId,
        @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal horas,
        Long responsavelId,
        Boolean indivisivel,
        Boolean doCliente,
        @Min(1) Integer diasCliente,
        Long contatoClienteId,
        Integer ordem
) {}
