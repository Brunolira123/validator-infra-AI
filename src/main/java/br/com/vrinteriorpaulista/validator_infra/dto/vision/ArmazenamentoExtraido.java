package br.com.vrinteriorpaulista.validator_infra.dto.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArmazenamentoExtraido(
        CampoExtraido<String> tipo,
        CampoExtraido<Integer> capacidade_gb
) {}
