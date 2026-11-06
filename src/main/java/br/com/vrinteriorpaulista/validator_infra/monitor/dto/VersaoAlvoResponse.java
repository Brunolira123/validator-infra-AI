package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

/** GET /api/monitor/agente/atualizacao quando há versão para o vrip-updater instalar. */
public record VersaoAlvoResponse(
        String versaoAlvo
) {}
