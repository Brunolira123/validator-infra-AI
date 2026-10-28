package br.com.vrinteriorpaulista.validator_infra.exception;

/** Limite de uso de serviço externo pago (trava diária do app ou limite do próprio Google): vira 429. */
public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException(String mensagem) {
        super(mensagem);
    }
}
