package br.com.vrinteriorpaulista.validator_infra.prospeccao.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Resposta do places:searchText. Sem resultado, o Google devolve "{}" (places e nextPageToken nulos). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaGoogle(List<LugarGoogle> places, String nextPageToken) {

    public List<LugarGoogle> lugares() {
        return places == null ? List.of() : places;
    }
}
