package br.com.vrinteriorpaulista.validator_infra.exception;

public class ServicoIndisponivelException extends IllegalStateException {

    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
