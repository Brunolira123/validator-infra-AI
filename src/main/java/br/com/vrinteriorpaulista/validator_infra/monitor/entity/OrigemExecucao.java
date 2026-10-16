package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

public enum OrigemExecucao {
    /** Rotina diária do agente (e as repetições de hora em hora enquanto houver falha). */
    AGENDADO,
    /** Resposta a um "Consultar agora" pedido no portal. */
    SOB_DEMANDA
}
