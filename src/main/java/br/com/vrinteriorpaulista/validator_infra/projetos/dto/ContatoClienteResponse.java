package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.ContatoCliente;

public record ContatoClienteResponse(Long id, Long clienteId, String nome, String email, String telefone, String setor,
                                     boolean ativo) {

    public static ContatoClienteResponse from(ContatoCliente c) {
        return new ContatoClienteResponse(c.getId(), c.getCliente().getId(), c.getNome(), c.getEmail(), c.getTelefone(),
                c.getSetor(), Boolean.TRUE.equals(c.getAtivo()));
    }
}
