package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record VersaoAlvoLoteRequest(
        @NotBlank @Pattern(regexp = VersaoAgente.REGEX, message = VersaoAgente.MENSAGEM) String versao,
        @NotNull EscopoVersaoAlvo escopo,
        /** Só no escopo IDS. */
        List<Long> ids
) {}
