package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import java.time.Instant;

public record ClienteMonitoradoResponse(
        Long id,
        String nome,
        String cnpj,
        boolean ativo,
        SituacaoCliente situacao,
        Instant ultimoContato,
        String versaoAgente,
        /** Recebimento da última execução (agendada ou sob demanda). */
        Instant ultimaExecucaoEm,
        long totalLojas,
        long lojasComFalha
) {}
