package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.LevantamentoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.EquipamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.LevantamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.RelatorioLevantamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.service.LevantamentoService;
import br.com.vrinteriorpaulista.validator_infra.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Levantamentos", description = "Levantamentos de infraestrutura, geração de equipamentos e relatório consolidado")
@RequestMapping("/api/levantamentos")
public class LevantamentoController {

    private final LevantamentoService service;
    private final RelatorioService relatorioService;

    public LevantamentoController(LevantamentoService service, RelatorioService relatorioService) {
        this.service = service;
        this.relatorioService = relatorioService;
    }

    @Operation(summary = "Cria um levantamento em rascunho")
    @PostMapping
    public LevantamentoResponse criar(@RequestBody @Valid LevantamentoRequest req) {
        return LevantamentoResponse.from(service.criar(req));
    }

    @Operation(summary = "Gera os equipamentos a partir do dimensionamento", description = "Retorna 409 se o levantamento já tiver equipamentos.")
    @PostMapping("/{id}/gerar-equipamentos")
    public LevantamentoResponse gerarEquipamentos(@PathVariable Long id) {
        return LevantamentoResponse.from(service.gerarEquipamentos(id));
    }

    @Operation(summary = "Conclui o levantamento", description = "Retorna 409 com as sequências pendentes se algum equipamento ainda não foi analisado.")
    @PostMapping("/{id}/concluir")
    public LevantamentoResponse concluir(@PathVariable Long id) {
        return LevantamentoResponse.from(service.concluir(id));
    }

    @Operation(summary = "Busca um levantamento pelo id")
    @GetMapping("/{id}")
    public LevantamentoResponse buscar(@PathVariable Long id) {
        return LevantamentoResponse.from(service.buscar(id));
    }

    @Operation(summary = "Lista os equipamentos do levantamento")
    @GetMapping("/{id}/equipamentos")
    public List<EquipamentoResponse> listarEquipamentos(@PathVariable Long id) {
        return service.buscar(id).getEquipamentos().stream()
                .map(EquipamentoResponse::from)
                .toList();
    }

    @Operation(summary = "Relatório consolidado do levantamento", description = "Resumo do dimensionamento, contagem por resultado e detalhe de cada equipamento com o resumo da análise.")
    @GetMapping("/{id}/relatorio")
    public RelatorioLevantamentoDTO relatorio(@PathVariable Long id) {
        return relatorioService.gerar(id);
    }
}
