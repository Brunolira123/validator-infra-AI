package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * chavePai e dependeDe apontam para chaves do mesmo template. horas null = horas padrão da categoria ao aplicar.
 * ordem null = posição na lista.
 */
public record TemplateAtividadeRequest(
        @NotBlank @Size(max = 50) String chave,
        @Size(max = 50) String chavePai,
        @NotBlank @Size(max = 200) String nome,
        Long categoriaId,
        @DecimalMin("0") @Digits(integer = 5, fraction = 2) BigDecimal horas,
        List<String> dependeDe,
        Boolean indivisivel,
        Boolean doCliente,
        @Min(1) Integer diasCliente,
        Integer ordem
) {}
