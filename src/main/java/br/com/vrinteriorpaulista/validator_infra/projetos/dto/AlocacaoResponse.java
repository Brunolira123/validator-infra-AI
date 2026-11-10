package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.TipoAlocacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/** atividadeId null nas viagens (tipo VIAGEM). */
public record AlocacaoResponse(Long usuarioId, String usuarioNome, Long atividadeId, LocalDate data, BigDecimal horas,
                               TipoAlocacao tipo) {}
