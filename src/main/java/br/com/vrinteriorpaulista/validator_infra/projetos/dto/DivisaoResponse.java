package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;

public record DivisaoResponse(Long usuarioId, String usuarioNome, BigDecimal horas) {}
