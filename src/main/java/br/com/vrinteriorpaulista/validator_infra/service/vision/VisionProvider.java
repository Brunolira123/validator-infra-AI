package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;

public interface VisionProvider {
    AnaliseEquipamentoDTO analisarImagem(byte[] imagemBytes, String mimeType);
}
