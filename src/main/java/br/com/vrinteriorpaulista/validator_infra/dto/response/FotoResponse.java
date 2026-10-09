package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.entity.Foto;

import java.time.LocalDateTime;

public record FotoResponse(
        Long id,
        String nomeOriginal,
        String contentType,
        Long tamanhoBytes,
        Integer sequencia,
        LocalDateTime criadoEm
) {
    public static FotoResponse from(Foto f) {
        return new FotoResponse(
                f.getId(), f.getNomeOriginal(), f.getContentType(),
                f.getTamanhoBytes(), f.getSequencia(), f.getCriadoEm()
        );
    }
}
