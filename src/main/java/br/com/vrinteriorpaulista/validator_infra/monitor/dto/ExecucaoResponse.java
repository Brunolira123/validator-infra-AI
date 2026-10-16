package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.OrigemExecucao;

import java.time.Instant;

public record ExecucaoResponse(
        Long id,
        OrigemExecucao origem,
        Instant executadoEm,
        Instant recebidoEm,
        boolean ok,
        String erro,
        long totalLojas,
        long lojasComFalha
) {
    public static ExecucaoResponse from(Execucao e) {
        return new ExecucaoResponse(e.getId(), e.getOrigem(), e.getExecutadoEm(), e.getRecebidoEm(),
                e.getOk(), e.getErro(), e.totalLojas(), e.lojasComFalha());
    }
}
