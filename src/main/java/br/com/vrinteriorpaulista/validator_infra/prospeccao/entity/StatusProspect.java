package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

/** Funil do prospect de campo: NOVO (salvo da busca) -> VISITADO -> LTO (virou Cliente + Levantamento), ou DESCARTADO. */
public enum StatusProspect {
    NOVO,
    VISITADO,
    LTO,
    DESCARTADO
}
