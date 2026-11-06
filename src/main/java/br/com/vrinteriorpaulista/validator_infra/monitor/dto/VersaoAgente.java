package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

/** Formato das versões do agente definidas pelo Suporte: 1.2.3 ou 1.2.3-rc.1. */
public final class VersaoAgente {

    public static final String REGEX = "^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.]+)?$";
    public static final String MENSAGEM = "versão deve ser no formato 1.2.3 ou 1.2.3-rc.1";

    private VersaoAgente() {
    }
}
