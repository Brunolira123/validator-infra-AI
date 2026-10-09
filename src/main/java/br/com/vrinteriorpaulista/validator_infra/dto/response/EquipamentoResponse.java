package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Equipamento;

public record EquipamentoResponse(
        Long id,
        String categoria,
        String funcao,
        Integer sequencia,
        String status,
        int qtdFotos
) {
    public static EquipamentoResponse from(Equipamento e) {
        return new EquipamentoResponse(
                e.getId(),
                e.getCategoria().name(),
                e.getFuncao().name(),
                e.getSequencia(),
                e.getStatus().name(),
                e.getFotos() != null ? e.getFotos().size() : 0
        );
    }
}
