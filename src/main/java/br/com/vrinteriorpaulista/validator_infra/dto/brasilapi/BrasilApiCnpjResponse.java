package br.com.vrinteriorpaulista.validator_infra.dto.brasilapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrasilApiCnpjResponse(
        String cnpj,
        String razao_social,
        String nome_fantasia,
        String logradouro,
        String numero,
        String bairro,
        String municipio,
        String uf,
        String ddd_telefone_1,
        String email,
        Boolean opcao_pelo_simples,
        Boolean opcao_pelo_mei,
        String porte,
        /** Só vem para parte dos CNPJs (empresas que entregam a ECF). */
        List<RegimeTributario> regime_tributario
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegimeTributario(Integer ano, String forma_de_tributacao) {}
}
