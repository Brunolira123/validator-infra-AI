package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LinhaCalculoResponse(Long atividadeId, String nome, LocalDate inicio, LocalDate fim, BigDecimal horas,
                                   boolean resumo, boolean doCliente) {}
