package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta do polling quando há pedido. CONSULTA: o agente consulta e envia em /resultados com este pedidoId.
 * LOG: lê as últimas {@code linhas} do {@code container} e envia em /pedidos/{pedidoId}/log.
 * <p>
 * Agente sem suporte a log (até a 2.2.x) recebe só {@code {"pedidoId": n}}, como antes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoAgenteResponse(
        Long pedidoId,
        TipoPedido tipo,
        String container,
        Integer linhas
) {
    public static PedidoAgenteResponse from(PedidoConsulta p, boolean contratoComTipo) {
        if (!contratoComTipo) {
            return new PedidoAgenteResponse(p.getId(), null, null, null);
        }
        return new PedidoAgenteResponse(p.getId(), p.getTipo(), p.getContainer(), p.getLinhas());
    }
}
