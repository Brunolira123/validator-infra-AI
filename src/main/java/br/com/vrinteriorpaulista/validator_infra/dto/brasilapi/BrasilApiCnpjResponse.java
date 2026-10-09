package br.com.vrinteriorpaulista.validator_infra.dto.brasilapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrasilApiCnpjResponse(
        String cnpj,
        String razao_social,
        String nome_fantasia,
        String logradouro,
        String numero,
        String bairro,
        String municipio,
        String uf,
        String ddd_telefone_1,
        String email
) {}
