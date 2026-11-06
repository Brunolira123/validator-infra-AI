package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

public enum EscopoVersaoAlvo {
    /** Clientes ativos marcados como piloto. */
    PILOTO,
    /** Todos os clientes ativos. */
    TODOS,
    /** Os clientes ativos de {@code ids}. */
    IDS
}
