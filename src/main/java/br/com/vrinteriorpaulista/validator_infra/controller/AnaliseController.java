package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.RevisaoAnaliseRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.FotoResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.service.AnaliseService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/equipamentos")
public class AnaliseController {

    private final AnaliseService service;

    public AnaliseController(AnaliseService service) {
        this.service = service;
    }

    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FotoResponse uploadFoto(@PathVariable Long id,
                                   @RequestParam("usuarioId") Long usuarioId,
                                   @RequestParam("foto") MultipartFile foto) {
        return FotoResponse.from(service.uploadFoto(id, usuarioId, foto));
    }

    @PostMapping("/{id}/analisar")
    public ResultadoAnaliseDTO analisar(@PathVariable Long id,
                                        @RequestParam("usuarioId") Long usuarioId) {
        return service.analisarEquipamento(id, usuarioId);
    }

    @PutMapping("/{id}/analise/revisar")
    public ResultadoAnaliseDTO revisar(@PathVariable Long id,
                                       @RequestBody @Valid RevisaoAnaliseRequest req) {
        return service.revisarAnalise(id, req);
    }
}
