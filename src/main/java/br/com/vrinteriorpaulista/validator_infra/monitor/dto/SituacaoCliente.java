package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

public enum SituacaoCliente {
    /** Cadastrado, mas o agente nunca enviou resultado. */
    AGUARDANDO,
    OK,
    /** A última execução tem verificação falhando. */
    FALHA,
    /** Agente sem contato além do limite (app.monitor.offline-apos). Tem precedência sobre OK/FALHA. */
    OFFLINE,
    INATIVO
}
