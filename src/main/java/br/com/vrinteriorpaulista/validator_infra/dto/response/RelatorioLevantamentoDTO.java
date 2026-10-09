package br.com.vrinteriorpaulista.validator_infra.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record RelatorioLevantamentoDTO(
        Long levantamentoId,
        Long clienteId,
        String clienteRazaoSocial,
        String clienteCnpj,
        String comercialNome,
        LocalDateTime data,
        ResumoDimensionamento resumo,
        ResumoResultados resultados,
        List<EquipamentoDetalhe> equipamentos
) {
    public record ResumoDimensionamento(
            int servidores,
            int pdvs,
            int retaguardas,
            int consultaPreco
    ) {}

    public record ResumoResultados(
            int total,
            int atende,
            int naoAtende,
            int requerAnalise,
            int pendente
    ) {}

    public record EquipamentoDetalhe(
            Long id,
            String categoria,
            String funcao,
            Integer sequencia,
            String status,
            String resultado,
            String justificativa,
            AnaliseResumo analise
    ) {}

    public record AnaliseResumo(
            String fabricante,
            String modelo,
            String cpuModelo,
            Integer cpuGeracao,
            Integer ramGb,
            String soNome,
            Double confiancaGlobal
    ) {}
}
