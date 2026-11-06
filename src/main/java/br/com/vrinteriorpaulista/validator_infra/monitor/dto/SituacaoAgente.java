package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

public enum SituacaoAgente {
    ONLINE,
    /** Sem contato além do limite (app.monitor.offline-apos) ou agente que nunca falou com o servidor. */
    OFFLINE
}
