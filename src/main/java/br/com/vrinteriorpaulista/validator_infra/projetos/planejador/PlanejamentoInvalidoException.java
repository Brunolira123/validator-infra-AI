package br.com.vrinteriorpaulista.validator_infra.projetos.planejador;

import java.util.List;

/** Todos os problemas encontrados no planejamento, juntos, para o usuário corrigir de uma vez. */
public class PlanejamentoInvalidoException extends RuntimeException {

    private final List<String> erros;

    public PlanejamentoInvalidoException(List<String> erros) {
        super(String.join("; ", erros));
        this.erros = List.copyOf(erros);
    }

    public List<String> getErros() {
        return erros;
    }
}
