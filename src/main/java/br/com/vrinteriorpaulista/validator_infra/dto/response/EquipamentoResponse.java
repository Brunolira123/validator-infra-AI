package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;

public record EquipamentoResponse(
        Long id,
        String categoria,
        String funcao,
        Integer sequencia,
        String status,
        int qtdFotos
) {
    /**
     * Usado pela projeção JPQL (select new ...) em EquipamentoRepository.
     */
    public EquipamentoResponse(Long id,
                               CategoriaEquipamento categoria,
                               FuncaoEquipamento funcao,
                               Integer sequencia,
                               StatusAnalise status,
                               Long qtdFotos) {
        this(id, categoria.name(), funcao.name(), sequencia, status.name(), qtdFotos.intValue());
    }
}
