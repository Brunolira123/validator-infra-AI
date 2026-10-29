package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

/**
 * Funil do prospect de campo: NOVO (salvo da busca) -> VISITADO -> FECHADO (contrato fechado, virou Cliente)
 * -> LTO (Levantamento criado). Saídas: DESCARTADO, ou PERDIDO depois da visita (pode ser visitado de novo).
 */
public enum StatusProspect {
    NOVO,
    VISITADO,
    FECHADO,
    LTO,
    PERDIDO,
    DESCARTADO
}
