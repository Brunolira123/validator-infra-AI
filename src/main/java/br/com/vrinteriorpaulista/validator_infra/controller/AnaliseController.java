package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.RevisaoAnaliseRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.FotoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.service.AnaliseService;
import br.com.vrinteriorpaulista.validator_infra.service.FotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@Tag(name = "Análise de equipamentos", description = "Upload de fotos, análise por IA + motor de regras e revisão humana")
@RequestMapping("/api/equipamentos")
public class AnaliseController {

    private final AnaliseService service;
    private final FotoService fotoService;

    public AnaliseController(AnaliseService service, FotoService fotoService) {
        this.service = service;
        this.fotoService = fotoService;
    }

    @PreAuthorize("hasAnyRole('COMERCIAL', 'ADMIN')")
    @Operation(summary = "Envia uma foto do equipamento", description = "Aceita image/jpeg, image/png e image/webp.")
    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoResponse uploadFoto(@PathVariable Long id,
                                   @RequestParam("foto") MultipartFile foto) {
        return FotoResponse.from(service.uploadFoto(id, foto));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'COMERCIAL', 'TECNICO')")
    @Operation(summary = "Lista os metadados das fotos do equipamento", description = "Ordenadas por sequência. Retorna 404 se o equipamento não existir.")
    @GetMapping("/{id}/fotos")
    public List<FotoResponse> listarFotos(@PathVariable Long id) {
        return fotoService.listarFotos(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'COMERCIAL', 'TECNICO')")
    @Operation(summary = "Baixa o arquivo da foto", description = "Exige token, como as demais rotas. Retorna 404 se a foto não existir ou não pertencer ao equipamento.")
    @GetMapping("/{id}/fotos/{fotoId}/conteudo")
    public ResponseEntity<Resource> conteudoFoto(@PathVariable Long id, @PathVariable Long fotoId) {
        FotoService.ConteudoFoto foto = fotoService.carregarConteudoFoto(id, fotoId);

        ResponseEntity.BodyBuilder resposta = ResponseEntity.ok().contentType(foto.contentType());
        if (foto.nomeOriginal() != null) {
            resposta.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                    .filename(foto.nomeOriginal(), StandardCharsets.UTF_8)
                    .build()
                    .toString());
        }
        return resposta.body(foto.arquivo());
    }

    @PreAuthorize("hasAnyRole('COMERCIAL', 'ADMIN')")
    @Operation(summary = "Analisa o equipamento pela última foto", description = "Extrai as especificações com IA e avalia contra os requisitos vigentes e a base de homologados. Retorna 503 se o provedor de IA estiver indisponível.")
    @PostMapping("/{id}/analisar")
    public ResultadoAnaliseDTO analisar(@PathVariable Long id) {
        return service.analisarEquipamento(id);
    }

    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Revisa a análise com dados corrigidos por um técnico", description = "Só os campos enviados são alterados. Reavalia com o motor de regras e guarda os dados corrigidos.")
    @PutMapping("/{id}/analise/revisar")
    public ResultadoAnaliseDTO revisar(@PathVariable Long id,
                                       @RequestBody @Valid RevisaoAnaliseRequest req) {
        return service.revisarAnalise(id, req);
    }
}
