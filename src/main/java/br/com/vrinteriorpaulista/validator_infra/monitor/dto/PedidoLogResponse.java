package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;

import java.time.Instant;

public record PedidoLogResponse(
        StatusPedido status,
        /** Só quando CONCLUIDO e ainda dentro da retenção (depois disso: null e textoApagadoEm preenchido). */
        String texto,
        boolean truncado,
        /** Quando ERRO: o motivo informado pelo agente. */
        String erro,
        /** Quando o agente devolveu o log (CONCLUIDO ou ERRO). */
        Instant recebidoEm,
        Instant textoApagadoEm
) {
    public static PedidoLogResponse from(PedidoConsulta p) {
        return new PedidoLogResponse(p.getStatus(), p.getTexto(), Boolean.TRUE.equals(p.getTruncado()),
                p.getErro(), p.getConcluidoEm(), p.getTextoApagadoEm());
    }
}
