package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.time.LocalDate;

public record FeriadoExtraResponse(Long id, LocalDate data, String descricao) {}
