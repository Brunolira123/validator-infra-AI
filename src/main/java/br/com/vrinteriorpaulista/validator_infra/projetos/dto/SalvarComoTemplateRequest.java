package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.Size;

/** templateId = nova versão desse template; sem ele, cria um template novo com o nome informado. */
public record SalvarComoTemplateRequest(Long templateId, @Size(max = 150) String nome) {}
