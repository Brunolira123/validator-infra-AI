package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.math.BigDecimal;
import java.util.List;

public record AtividadeResponse(Long id, Long paiId, String nome, Long categoriaId, String categoriaNome,
                                BigDecimal horas, Long responsavelId, String responsavelNome, boolean indivisivel,
                                boolean doCliente, int diasCliente, Long contatoClienteId, String contatoClienteNome,
                                int ordem, List<Long> dependeDe, List<DivisaoResponse> distribuicao) {}
