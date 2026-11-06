package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Foto do ambiente do cliente coletada pelo agente. {@code dockerDisponivel=false} ou {@code erro} preenchido
 * vêm com {@code containers} vazia.
 */
public record AmbienteAgenteRequest(
        @NotNull OffsetDateTime coletadoEm,
        @Size(max = 30) String versaoAgente,
        @NotNull Boolean dockerDisponivel,
        @Size(max = 500) String erro,
        @NotNull @Size(max = 100) @Valid List<Container> containers
) {
    public record Container(
            @NotBlank @Size(max = 100) String nome,
            @Size(max = 100) String versao,
            @NotNull Boolean rodando
    ) {}
}
