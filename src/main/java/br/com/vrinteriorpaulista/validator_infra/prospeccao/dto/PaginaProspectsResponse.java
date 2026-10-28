package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import java.util.List;

/** pagina começa em 0. O Google só é consultado para os prospects desta página. */
public record PaginaProspectsResponse(
        List<ProspectResponse> prospects,
        int pagina,
        int totalPaginas,
        long total
) {}
