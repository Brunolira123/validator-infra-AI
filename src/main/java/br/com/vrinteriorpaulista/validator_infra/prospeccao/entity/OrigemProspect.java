package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

/** De onde veio o prospect. Por enquanto só GOOGLE é usado; CRM fica para a importação do Bitrix24. */
public enum OrigemProspect {
    GOOGLE,
    MANUAL,
    CRM
}
