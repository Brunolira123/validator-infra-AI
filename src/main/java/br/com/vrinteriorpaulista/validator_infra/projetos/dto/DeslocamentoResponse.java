package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeslocamentoResponse(Long id, Long usuarioId, String usuarioNome, LocalDate data, BigDecimal horas,
                                   String descricao) {}
