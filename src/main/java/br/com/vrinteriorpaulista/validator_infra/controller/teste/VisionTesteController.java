package br.com.vrinteriorpaulista.validator_infra.controller.teste;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.service.vision.VisionProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/vision")
public class VisionTesteController {

    private final VisionProvider visionProvider;

    public VisionTesteController(VisionProvider visionProvider) {
        this.visionProvider = visionProvider;
    }

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
