package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Template;

import java.time.LocalDateTime;
import java.util.List;

public record TemplateResponse(Long id, String nome, int versao, boolean ativo, LocalDateTime atualizadoEm,
                               List<TemplateAtividadeResponse> atividades) {

    public static TemplateResponse from(Template t) {
        return new TemplateResponse(t.getId(), t.getNome(), t.getVersao(), Boolean.TRUE.equals(t.getAtivo()),
                t.getAtualizadoEm(), t.getAtividades().stream().map(TemplateAtividadeResponse::from).toList());
    }
}
