package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

public enum StatusPedido {
    /** Aguardando o agente buscar. */
    PENDENTE,
    /** O agente buscou e está consultando o banco do cliente. */
    ENTREGUE,
    CONCLUIDO,
    /** O agente não buscou ou não respondeu dentro da validade (agente offline). */
    EXPIRADO
}
