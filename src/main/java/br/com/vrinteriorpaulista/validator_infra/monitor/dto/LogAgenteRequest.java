package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.constraints.Size;

/**
 * Log de container enviado pelo agente. {@code erro} preenchido = não conseguiu ler (o pedido vira ERRO).
 * O texto tem até 256 KB em UTF-8; o @Size só barra cedo o que já passa disso em caracteres.
 */
public record LogAgenteRequest(
        @Size(max = LogAgenteRequest.MAX_BYTES) String texto,
        Boolean truncado,
        @Size(max = 500) String erro
) {
    public static final int MAX_BYTES = 256 * 1024;
}
