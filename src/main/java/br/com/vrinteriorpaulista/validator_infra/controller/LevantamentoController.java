package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.LevantamentoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.EquipamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.LevantamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.RelatorioLevantamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.service.LevantamentoService;
import br.com.vrinteriorpaulista.validator_infra.service.RelatorioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levantamentos")
public class LevantamentoController {

    private final LevantamentoService service;
    private final RelatorioService relatorioService;


    public LevantamentoController(LevantamentoService service, RelatorioService relatorioService) {
        this.service = service;
        this.relatorioService = relatorioService;
    }

    @PostMapping
    public LevantamentoResponse criar(@RequestBody @Valid LevantamentoRequest req) {
        return LevantamentoResponse.from(service.criar(req));
    }

    @PostMapping("/{id}/gerar-equipamentos")
    public LevantamentoResponse gerarEquipamentos(@PathVariable Long id) {
        return LevantamentoResponse.from(service.gerarEquipamentos(id));
    }

    @PostMapping("/{id}/concluir")
    public LevantamentoResponse concluir(@PathVariable Long id) {
        return LevantamentoResponse.from(service.concluir(id));
    }

    @GetMapping("/{id}")
    public LevantamentoResponse buscar(@PathVariable Long id) {
        return LevantamentoResponse.from(service.buscar(id));
    }

    @GetMapping("/{id}/equipamentos")
    public List<EquipamentoResponse> listarEquipamentos(@PathVariable Long id) {
        return service.buscar(id).getEquipamentos().stream()
                .map(EquipamentoResponse::from)
                .toList();
    }

    @GetMapping("/{id}/relatorio")
    public RelatorioLevantamentoDTO relatorio(@PathVariable Long id) {
        return relatorioService.gerar(id);
    }
}
