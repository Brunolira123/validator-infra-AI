package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Template;

import java.time.LocalDateTime;

public record TemplateResumoResponse(Long id, String nome, int versao, boolean ativo, int qtdAtividades,
                                     LocalDateTime atualizadoEm) {

    public static TemplateResumoResponse from(Template t) {
        return new TemplateResumoResponse(t.getId(), t.getNome(), t.getVersao(), Boolean.TRUE.equals(t.getAtivo()),
                t.getAtividades().size(), t.getAtualizadoEm());
    }
}
