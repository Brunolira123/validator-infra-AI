package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;

import java.time.Instant;

public record PedidoResponse(
        Long id,
        Long clienteId,
        StatusPedido status,
        Instant criadoEm,
        Instant concluidoEm,
        /** Preenchido quando CONCLUIDO: o resultado está no detalhe do cliente. */
        Long execucaoId
) {
    public static PedidoResponse from(PedidoConsulta p) {
        return new PedidoResponse(p.getId(), p.getCliente().getId(), p.getStatus(), p.getCriadoEm(),
                p.getConcluidoEm(), p.getExecucao() != null ? p.getExecucao().getId() : null);
    }
}
