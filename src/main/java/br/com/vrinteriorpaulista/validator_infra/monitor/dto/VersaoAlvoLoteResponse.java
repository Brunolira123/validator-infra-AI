package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

/** Clientes que passaram a ter a versão como alvo (os que já rodam essa versão ficam de fora). */
public record VersaoAlvoLoteResponse(
        int marcados
) {}
