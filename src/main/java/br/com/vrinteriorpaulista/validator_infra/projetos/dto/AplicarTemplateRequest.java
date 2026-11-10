package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.NotNull;

/** substituir = true apaga as atividades atuais; sem ele, planejamento com atividades dá 409. */
public record AplicarTemplateRequest(@NotNull Long templateId, Boolean substituir) {}
