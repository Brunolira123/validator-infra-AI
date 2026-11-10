package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Categoria;

import java.math.BigDecimal;

public record CategoriaResponse(Long id, String nome, BigDecimal horasPadrao, boolean ativo) {

    public static CategoriaResponse from(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.getHorasPadrao(), Boolean.TRUE.equals(c.getAtivo()));
    }
}
