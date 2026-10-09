package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;

import java.time.LocalDateTime;

public record LevantamentoResponse(
        Long id,
        Long clienteId,
        String clienteRazaoSocial,
        Long usuarioId,
        String usuarioNome,
        Integer qtdServidores,
        Integer qtdPdvs,
        Integer qtdRetaguardas,
        Boolean consultaPreco,
        Integer qtdConsultaPreco,
        String outros,
        String status,
        LocalDateTime criadoEm
) {
    public static LevantamentoResponse from(Levantamento l) {
        return new LevantamentoResponse(
                l.getId(),
                l.getCliente().getId(),
                l.getCliente().getRazaoSocial(),
                l.getUsuario().getId(),
                l.getUsuario().getNome(),
                l.getQtdServidores(),
                l.getQtdPdvs(),
                l.getQtdRetaguardas(),
                l.getConsultaPreco(),
                l.getQtdConsultaPreco(),
                l.getOutros(),
                l.getStatus().name(),
                l.getCriadoEm()
        );
    }
}
