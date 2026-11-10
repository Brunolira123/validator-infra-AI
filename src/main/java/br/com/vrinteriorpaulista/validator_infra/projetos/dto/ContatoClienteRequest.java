package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ativo null = true. */
public record ContatoClienteRequest(
        @NotBlank @Size(max = 100) String nome,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        @Size(max = 100) String setor,
        Boolean ativo
) {}
