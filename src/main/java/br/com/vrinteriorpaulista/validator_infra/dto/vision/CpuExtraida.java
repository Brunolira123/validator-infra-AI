package br.com.vrinteriorpaulista.validator_infra.dto.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CpuExtraida(
        CampoExtraido<String> fabricante,
        CampoExtraido<String> modelo,
        CampoExtraido<Integer> geracao,
        CampoExtraido<Integer> cores,
        CampoExtraido<Integer> threads
) {}
