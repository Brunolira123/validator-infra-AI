package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import java.util.List;

/** proximaPagina é um token opaco: devolva como "pagina" para carregar mais; null = acabou. */
public record PaginaBuscaProspectResponse(
        List<LugarEncontradoResponse> lugares,
        String proximaPagina
) {}
