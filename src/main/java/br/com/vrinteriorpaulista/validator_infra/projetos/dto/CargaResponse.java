package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Horas de um analista num dia (serviço + viagem), somando o último cálculo dos planejamentos que ocupam agenda. */
public record CargaResponse(Long usuarioId, String usuarioNome, LocalDate data, BigDecimal horas,
                            List<CargaPlanejamentoResponse> planejamentos) {

    public record CargaPlanejamentoResponse(Long planejamentoId, String nome, BigDecimal horas) {}
}
