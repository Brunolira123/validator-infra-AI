package br.com.vrinteriorpaulista.validator_infra.prospeccao.google;

/** Bytes da foto baixada do Google (fica só no cache em memória, nunca em disco). */
public record FotoGoogle(byte[] bytes, String contentType) {}
