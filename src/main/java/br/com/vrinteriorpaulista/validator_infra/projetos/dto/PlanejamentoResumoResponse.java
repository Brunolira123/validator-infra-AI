package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanejamentoResumoResponse(Long id, Long clienteId, String clienteNome, Long levantamentoId, String nome,
                                         SituacaoPlanejamento situacao, String templateNome, Integer templateVersao,
                                         LocalDate kickoff, LocalDate inicioCalculado, LocalDate terminoCalculado,
                                         Long responsavelId, String responsavelNome, LocalDateTime calculadoEm,
                                         boolean calculoDesatualizado, LocalDateTime atualizadoEm) {}
