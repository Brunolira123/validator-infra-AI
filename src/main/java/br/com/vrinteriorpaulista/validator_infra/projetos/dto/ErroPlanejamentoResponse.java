package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 400 do cálculo: todos os problemas encontrados, legíveis, para corrigir de uma vez. */
public record ErroPlanejamentoResponse(String mensagem, LocalDateTime timestamp, String path, List<String> erros) {}
