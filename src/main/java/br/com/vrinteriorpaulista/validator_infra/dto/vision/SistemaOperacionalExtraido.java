package br.com.vrinteriorpaulista.validator_infra.dto.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SistemaOperacionalExtraido(
        CampoExtraido<String> nome,
        CampoExtraido<String> versao
) {}
