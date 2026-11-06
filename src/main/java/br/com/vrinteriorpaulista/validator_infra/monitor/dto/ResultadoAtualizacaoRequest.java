package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusAtualizacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O que o vrip-updater informa depois de tentar instalar a versão alvo. */
public record ResultadoAtualizacaoRequest(
        @Size(max = 30) String versaoAnterior,
        @NotBlank @Size(max = 30) String versaoAlvo,
        @NotNull StatusAtualizacao status,
        @Size(max = 500) String detalhe
) {}
