package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.*;

import java.time.LocalDateTime;

/**
 * Prospect salvo: dados nossos + "lugar" buscado de novo no Google na hora (termos do Google).
 * lugar vem null quando o lugar sumiu do Google, a busca está fora do ar ou o limite diário de detalhes acabou.
 */
public record ProspectResponse(
        Long id,
        OrigemProspect origem,
        String placeId,
        String nome,
        String cidade,
        String uf,
        CategoriaProspeccao categoria,
        StatusProspect status,
        ResultadoVisita resultadoVisita,
        TemperaturaNegociacao temperatura,
        LocalDateTime visitadoEm,
        String observacao,
        Long responsavelId,
        String responsavelNome,
        Long clienteId,
        LocalDateTime criadoEm,
        LugarEncontradoResponse lugar
) {
    public static ProspectResponse from(Prospect p, LugarEncontradoResponse lugar) {
        return new ProspectResponse(
                p.getId(),
                p.getOrigem(),
                p.getPlaceId(),
                p.getNome(),
                p.getCidade(),
                p.getUf(),
                p.getCategoria(),
                p.getStatus(),
                p.getResultadoVisita(),
                p.getTemperatura(),
                p.getVisitadoEm(),
                p.getObservacao(),
                p.getResponsavel().getId(),
                p.getResponsavel().getNome(),
                p.getCliente() != null ? p.getCliente().getId() : null,
                p.getCriadoEm(),
                lugar != null ? lugar.comProspect(p) : null
        );
    }
}
