package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import java.util.List;

/**
 * Filtros da lista única, todos combináveis (E). A exceção é a versão do banco: versaoBanco e semVersaoBanco
 * juntos valem como OU ("4.4.93 ou sem versão").
 */
public record FiltroClientes(
        String busca,
        List<SituacaoCliente> situacao,
        boolean comParado,
        List<String> versaoBanco,
        boolean semVersaoBanco,
        boolean agenteDesatualizado,
        Boolean piloto,
        boolean inativos
) {}
