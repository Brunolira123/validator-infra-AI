package br.com.vrinteriorpaulista.validator_infra.projetos.dto;

import java.util.List;

public record PaginaPlanejamentosResponse(List<PlanejamentoResumoResponse> planejamentos, int pagina, int totalPaginas,
                                          long totalItens) {}
