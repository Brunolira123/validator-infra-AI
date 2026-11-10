package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Feriado;

import java.time.LocalDate;

public record FeriadoResponse(Long id, LocalDate data, String descricao, EscopoFeriado escopo, String cidade, String uf) {

    public static FeriadoResponse from(Feriado f) {
        return new FeriadoResponse(f.getId(), f.getData(), f.getDescricao(), f.getEscopo(), f.getCidade(), f.getUf());
    }
}
