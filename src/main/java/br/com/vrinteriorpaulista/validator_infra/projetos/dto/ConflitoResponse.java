package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** semana = segunda-feira. horas = horas do analista em outros planejamentos nos dias que este cálculo usou. */
public record ConflitoResponse(Long usuarioId, String usuarioNome, LocalDate semana, BigDecimal horas,
                               String mensagem) {}
