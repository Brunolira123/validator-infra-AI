package br.com.vrinteriorpaulista.validator_infra.projetos.controller;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.ErroPlanejamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejamentoInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/** Antes do GlobalExceptionHandler, que trataria a exceção como erro interno. */
@RestControllerAdvice(basePackageClasses = ProjetosExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjetosExceptionHandler {

    @ExceptionHandler(PlanejamentoInvalidoException.class)
    public ResponseEntity<ErroPlanejamentoResponse> planejamentoInvalido(PlanejamentoInvalidoException ex,
                                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroPlanejamentoResponse(
                "Planejamento inválido", LocalDateTime.now(), req.getRequestURI(), ex.getErros()));
    }
}
