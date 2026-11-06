package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.Pattern;

/** versao null cancela a atualização pendente do cliente. */
public record VersaoAlvoClienteRequest(
        @Pattern(regexp = VersaoAgente.REGEX, message = VersaoAgente.MENSAGEM) String versao
) {}
