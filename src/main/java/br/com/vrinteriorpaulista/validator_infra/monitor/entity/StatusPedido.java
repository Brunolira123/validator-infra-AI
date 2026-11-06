package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

public enum StatusPedido {
    /** Aguardando o agente buscar. */
    PENDENTE,
    /** O agente buscou e está trabalhando no pedido. */
    ENTREGUE,
    CONCLUIDO,
    /** O agente respondeu, mas não conseguiu atender (ex.: container sumiu antes de ler o log). */
    ERRO,
    /** O agente não buscou ou não respondeu dentro da validade (agente offline). */
    EXPIRADO
}
