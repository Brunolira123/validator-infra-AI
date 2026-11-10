package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** calculo = último cálculo salvo (null se nunca calculou); calculoDesatualizado = houve edição depois dele. */
public record PlanejamentoResponse(Long id, Long clienteId, String clienteNome, String clienteCidade, String clienteUf,
                                   Long levantamentoId, String nome, SituacaoPlanejamento situacao,
                                   String templateNome, Integer templateVersao, LocalDate kickoff,
                                   BigDecimal horasContratadas, BigDecimal custoHoraServico,
                                   BigDecimal custoHoraViagem, Long responsavelId, String responsavelNome,
                                   String observacao, List<AtividadeResponse> atividades,
                                   List<DeslocamentoResponse> deslocamentos, List<FeriadoExtraResponse> feriadosExtras,
                                   CalculoResponse calculo, boolean calculoDesatualizado, LocalDateTime criadoEm,
                                   LocalDateTime atualizadoEm) {}
