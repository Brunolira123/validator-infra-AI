package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.TemplateAtividade;

import java.math.BigDecimal;
import java.util.List;

public record TemplateAtividadeResponse(String chave, String chavePai, String nome, Long categoriaId,
                                        String categoriaNome, BigDecimal horas, List<String> dependeDe,
                                        boolean indivisivel, boolean doCliente, int diasCliente, int ordem) {

    public static TemplateAtividadeResponse from(TemplateAtividade a) {
        return new TemplateAtividadeResponse(a.getChave(), a.getChavePai(), a.getNome(),
                a.getCategoria() != null ? a.getCategoria().getId() : null,
                a.getCategoria() != null ? a.getCategoria().getNome() : null,
                a.getHoras(), List.copyOf(a.getDependeDe()), Boolean.TRUE.equals(a.getIndivisivel()),
                Boolean.TRUE.equals(a.getDoCliente()), a.getDiasCliente(), a.getOrdem());
    }
}
