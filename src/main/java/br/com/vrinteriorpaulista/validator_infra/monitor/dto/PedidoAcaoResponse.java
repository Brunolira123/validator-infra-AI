package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;

import java.time.Instant;

public record PedidoAcaoResponse(
        TipoPedido tipo,
        StatusPedido status,
        /** Quando ERRO: o motivo informado pelo agente. */
        String erro,
        /** Quando o agente respondeu (CONCLUIDO ou ERRO). */
        Instant recebidoEm
) {
    public static PedidoAcaoResponse from(PedidoConsulta p) {
        return new PedidoAcaoResponse(p.getTipo(), p.getStatus(), p.getErro(), p.getConcluidoEm());
    }
}
