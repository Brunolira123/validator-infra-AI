package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO.ItemAvaliadoDTO;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Análise persistida do equipamento: resultado do motor de regras, itens avaliados e dados extraídos
 * (já com as correções do técnico, quando houver revisão).
 */
public record AnaliseResponse(
        StatusAnalise resultado,
        String justificativa,
        List<ItemAvaliadoDTO> itens,
        String fabricante,
        String modelo,
        String cpuFabricante,
        String cpuModelo,
        Integer cpuGeracao,
        Integer cpuCores,
        Integer cpuThreads,
        Integer ramGb,
        String armazenamentoTipo,
        Integer armazenamentoGb,
        String soNome,
        String soVersao,
        Double confiancaGlobal,
        LocalDateTime analisadoEm,
        String analisadoPorNome,
        boolean revisada
) {}
