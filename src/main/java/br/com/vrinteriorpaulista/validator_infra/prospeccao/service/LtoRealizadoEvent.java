package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

/** Publicado no LTO, dentro da transação; quem ouve deve agir depois do commit. */
public record LtoRealizadoEvent(Long prospectId, Long clienteId, Long levantamentoId) {}
