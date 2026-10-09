package br.com.vrinteriorpaulista.validator_infra.controller.teste;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.service.vision.VisionProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@Tag(name = "Testes (uso interno)", description = "Endpoints de diagnóstico que chamam a API paga de IA")
@RequestMapping("/api/vision")
public class VisionTesteController {

    private final VisionProvider visionProvider;

    public VisionTesteController(VisionProvider visionProvider) {
        this.visionProvider = visionProvider;
    }

    @Operation(summary = "Extrai as especificações de uma foto sem persistir")
    @PostMapping(value = "/teste", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnaliseEquipamentoDTO testar(@RequestParam("foto") MultipartFile foto) {
        return visionProvider.analisarImagem(bytes(foto), foto.getContentType());
    }

    private byte[] bytes(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a foto enviada", e);
        }
    }
}
