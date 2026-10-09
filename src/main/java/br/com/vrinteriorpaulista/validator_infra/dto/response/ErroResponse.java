package br.com.vrinteriorpaulista.validator_infra.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        String mensagem,
        LocalDateTime timestamp,
        String path,
        List<CampoInvalido> campos
) {
    public record CampoInvalido(
            String campo,
            String mensagem
    ) {}
}
