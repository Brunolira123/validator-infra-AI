package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** ativo null = true. As atividades substituem as anteriores (e a versão sobe). */
public record TemplateRequest(
        @NotBlank @Size(max = 150) String nome,
        Boolean ativo,
        @NotNull @Size(max = 500) List<@Valid @NotNull TemplateAtividadeRequest> atividades
) {}
