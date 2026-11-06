package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

public enum TipoPedido {
    /** "Consultar agora": o agente consulta o banco e envia em /resultados. */
    CONSULTA,
    /** Log de um container, pedido pelo Suporte: o agente envia em /pedidos/{id}/log. */
    LOG
}
