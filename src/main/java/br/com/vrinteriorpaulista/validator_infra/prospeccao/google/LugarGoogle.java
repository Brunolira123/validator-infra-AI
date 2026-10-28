package br.com.vrinteriorpaulista.validator_infra.prospeccao.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Um "place" da Places API (New), só com os campos da faixa Pro pedidos no FieldMask. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LugarGoogle(
        String id,
        Texto displayName,
        String formattedAddress,
        Localizacao location,
        List<Foto> photos,
        String businessStatus,
        String googleMapsUri
) {
    public static final String FECHADO_DEFINITIVAMENTE = "CLOSED_PERMANENTLY";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Texto(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Localizacao(Double latitude, Double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Foto(String name, List<Autor> authorAttributions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Autor(String displayName, String uri) {}
}
