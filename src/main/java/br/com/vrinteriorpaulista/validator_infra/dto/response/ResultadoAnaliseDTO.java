package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;

import java.util.List;

public record ResultadoAnaliseDTO(
        StatusAnalise resultado,
        List<ItemAvaliadoDTO> itens,
        String justificativa
) {
    public record ItemAvaliadoDTO(
            String campo,
            String valorEncontrado,
            String requisito,
            StatusAnalise status,
            String observacao
    ) {}
}
