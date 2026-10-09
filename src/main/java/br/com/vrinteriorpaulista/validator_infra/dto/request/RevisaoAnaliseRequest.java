package br.com.vrinteriorpaulista.validator_infra.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RevisaoAnaliseRequest(
        @NotNull Long usuarioId,
        @Size(max = 100) String fabricante,
        @Size(max = 150) String modelo,
        @Size(max = 50) String cpuFabricante,
        @Size(max = 100) String cpuModelo,
        @Positive Integer cpuGeracao,
        @Positive Integer cpuCores,
        @Positive Integer cpuThreads,
        @Positive Integer ramGb,
        @Size(max = 20) String armazenamentoTipo,
        @Positive Integer armazenamentoGb,
        @Size(max = 100) String soNome,
        @Size(max = 100) String soVersao,
        String observacoes
) {}
