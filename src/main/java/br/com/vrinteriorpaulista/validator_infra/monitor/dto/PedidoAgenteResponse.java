package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

/**
 * Resposta do polling quando há "Consultar agora": o agente consulta e envia o resultado com este pedidoId.
 */
public record PedidoAgenteResponse(
        Long pedidoId
) {}
