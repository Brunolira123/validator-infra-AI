package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

public enum StatusAtualizacao {
    ATUALIZADO,
    /** Não conseguiu atualizar e o agente seguiu na versão anterior. */
    FALHOU,
    /** Atualizou, a versão nova não subiu direito e o updater voltou para a anterior. */
    ROLLBACK
}
