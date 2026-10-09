package br.com.vrinteriorpaulista.validator_infra.dto.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnaliseEquipamentoDTO(
        CampoExtraido<String> fabricante,
        CampoExtraido<String> modelo,
        CpuExtraida cpu,
        MemoriaExtraida memoria,
        List<ArmazenamentoExtraido> armazenamento,
        SistemaOperacionalExtraido sistema_operacional,
        Double confianca_global,
        List<String> campos_inconclusivos,
        String observacoes,
        String erro
) {}
