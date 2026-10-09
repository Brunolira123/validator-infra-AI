package br.com.vrinteriorpaulista.validator_infra.controller.teste;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.service.MotorRegrasService;
import br.com.vrinteriorpaulista.validator_infra.service.vision.VisionProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/motor")
public class MotorRegrasTesteController {

    private final VisionProvider visionProvider;
    private final MotorRegrasService motorRegras;

    public MotorRegrasTesteController(VisionProvider visionProvider,
                                      MotorRegrasService motorRegras) {
        this.visionProvider = visionProvider;
        this.motorRegras = motorRegras;
    }

    @PostMapping(value = "/avaliar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoAnaliseDTO avaliar(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam("categoria") CategoriaEquipamento categoria,
            @RequestParam("funcao") FuncaoEquipamento funcao
    ) {

        AnaliseEquipamentoDTO dados = visionProvider.analisarImagem(
                bytes(foto),
                foto.getContentType()
        );

        return motorRegras.avaliar(dados, categoria, funcao);
    }

    private byte[] bytes(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a foto enviada", e);
        }
    }
}
