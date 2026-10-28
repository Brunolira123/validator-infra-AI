package br.com.vrinteriorpaulista.validator_infra.exception;

/** Trava de custo de serviço externo pago (ex.: Google Places): vira 429 no GlobalExceptionHandler. */
public class LimiteDiarioAtingidoException extends RuntimeException {

    public LimiteDiarioAtingidoException(String mensagem) {
        super(mensagem);
    }
}
