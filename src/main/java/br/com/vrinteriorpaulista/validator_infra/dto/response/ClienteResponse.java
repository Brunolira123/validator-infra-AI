package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;

public record ClienteResponse(
        Long id,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String cidade,
        String uf,
        String telefone,
        String email
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(), c.getCnpj(), c.getRazaoSocial(),
                c.getNomeFantasia(), c.getCidade(), c.getUf(),
                c.getTelefone(), c.getEmail()
        );
    }
}
