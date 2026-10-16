package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

/**
 * Única vez em que o token aparece: o banco guarda só o hash. Vai no vr.properties do agente (monitor.token).
 */
public record TokenAgenteResponse(
        ClienteMonitoradoResponse cliente,
        String token
) {}
