package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.RevisaoAnaliseRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.FotoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.service.AnaliseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Análise de equipamentos", description = "Upload de fotos, análise por IA + motor de regras e revisão humana")
@RequestMapping("/api/equipamentos")
public class AnaliseController {

    private final AnaliseService service;

    public AnaliseController(AnaliseService service) {
        this.service = service;
    }

    @Operation(summary = "Envia uma foto do equipamento", description = "Aceita image/jpeg, image/png e image/webp.")
    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoResponse uploadFoto(@PathVariable Long id,
                                   @RequestParam("usuarioId") Long usuarioId,
                                   @RequestParam("foto") MultipartFile foto) {
        return FotoResponse.from(service.uploadFoto(id, usuarioId, foto));
    }

    @Operation(summary = "Analisa o equipamento pela última foto", description = "Extrai as especificações com IA e avalia contra os requisitos vigentes e a base de homologados. Retorna 503 se o provedor de IA estiver indisponível.")
    @PostMapping("/{id}/analisar")
    public ResultadoAnaliseDTO analisar(@PathVariable Long id,
                                        @RequestParam("usuarioId") Long usuarioId) {
        return service.analisarEquipamento(id, usuarioId);
    }

    @Operation(summary = "Revisa a análise com dados corrigidos por um técnico", description = "Só os campos enviados são alterados. Reavalia com o motor de regras e guarda os dados corrigidos.")
    @PutMapping("/{id}/analise/revisar")
    public ResultadoAnaliseDTO revisar(@PathVariable Long id,
                                       @RequestBody @Valid RevisaoAnaliseRequest req) {
        return service.revisarAnalise(id, req);
    }
}
