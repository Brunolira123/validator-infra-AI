package br.com.vrinteriorpaulista.validator_infra.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
        @NotBlank String cnpj,
        @NotBlank String razaoSocial,
        String nomeFantasia,
        String endereco,
        String cidade,
        String uf,
        String telefone,
        String email
) {}
