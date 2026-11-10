package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Substitui as dependências da atividade (ids de atividades do mesmo planejamento). */
public record DependenciasRequest(@NotNull List<@NotNull Long> dependeDe) {}
