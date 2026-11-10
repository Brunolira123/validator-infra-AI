package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;
import jakarta.validation.constraints.NotNull;

public record SituacaoRequest(@NotNull SituacaoPlanejamento situacao) {}
