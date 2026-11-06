package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

/**
 * Um container Docker no servidor do cliente, como o agente viu na última coleta. Vive dentro do JSONB de
 * {@link AmbienteCliente}: não tem tabela própria.
 */
public record ContainerAmbiente(String nome, String versao, boolean rodando) {}
