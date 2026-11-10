package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FeriadoExtraRequest(@NotNull LocalDate data, @Size(max = 100) String descricao) {}
