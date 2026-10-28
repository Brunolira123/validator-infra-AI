package br.com.vrinteriorpaulista.validator_infra.prospeccao.dto;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Só o placeId do Google + o que o comercial buscou. Nome/endereço do Google não são guardados. */
public record SalvarProspectRequest(
        @NotBlank @Size(max = 300) String placeId,
        @NotBlank @Size(max = 80) String cidade,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida") String uf,
        CategoriaProspeccao categoria
) {}
