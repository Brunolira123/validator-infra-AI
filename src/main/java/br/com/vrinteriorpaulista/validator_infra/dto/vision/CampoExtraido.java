package br.com.vrinteriorpaulista.validator_infra.dto.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CampoExtraido<T>(
        T valor,
        Double confianca
) {
    public static final double CONFIANCA_MINIMA = 0.7;

    public boolean isConclusivo() {
        return valor != null && confianca != null && confianca >= CONFIANCA_MINIMA;
    }
}
