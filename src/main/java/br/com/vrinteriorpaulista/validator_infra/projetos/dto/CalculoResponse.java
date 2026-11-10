package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado do planejador, como fica salvo no planejamento. diasUteis = do kickoff ao término, inclusive.
 * custoInterno = horasServico × custoHoraServico + horasViagem × custoHoraViagem. saldoContrato = horasContratadas
 * menos horasServico (null sem horas contratadas).
 */
public record CalculoResponse(LocalDateTime calculadoEm, LocalDate kickoff, LocalDate inicio, LocalDate termino,
                              int diasUteis, BigDecimal horasServico, BigDecimal horasViagem, BigDecimal custoInterno,
                              BigDecimal horasContratadas, BigDecimal saldoContrato,
                              List<LinhaCalculoResponse> atividades, List<AlocacaoResponse> alocacoes,
                              List<ConflitoResponse> conflitos) {}
