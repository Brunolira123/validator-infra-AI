package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.response.FotoResponse;
import br.com.vrinteriorpaulista.validator_infra.entity.Foto;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.FotoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.List;

/**
 * Leitura das fotos já enviadas. O upload fica no AnaliseService.
 */
@Service
@Slf4j
public class FotoService {

    private static final String FOTO_NAO_ENCONTRADA = "Foto não encontrada";

    private final FotoRepository fotoRepo;
    private final EquipamentoRepository equipamentoRepo;

    public FotoService(FotoRepository fotoRepo, EquipamentoRepository equipamentoRepo) {
        this.fotoRepo = fotoRepo;
        this.equipamentoRepo = equipamentoRepo;
    }

    @Transactional(readOnly = true)
    public List<FotoResponse> listarFotos(Long equipamentoId) {
        if (!equipamentoRepo.existsById(equipamentoId)) {
            throw new EntityNotFoundException("Equipamento não encontrado");
        }
        return fotoRepo.findByEquipamentoIdOrderBySequenciaAsc(equipamentoId).stream()
                .map(FotoResponse::from)
                .toList();
    }

    /**
     * 404 se a foto não existir, não pertencer ao equipamento ou o arquivo não estiver mais no disco.
     */
    @Transactional(readOnly = true)
    public ConteudoFoto carregarConteudoFoto(Long equipamentoId, Long fotoId) {
        Foto foto = fotoRepo.findById(fotoId)
                .filter(f -> f.getEquipamento().getId().equals(equipamentoId))
                .orElseThrow(() -> new EntityNotFoundException(FOTO_NAO_ENCONTRADA));

        Resource arquivo = new FileSystemResource(Paths.get(foto.getCaminho()));
        if (!arquivo.isReadable()) {
            log.warn("Foto {} registrada no banco, mas o arquivo não existe em {}", foto.getId(), foto.getCaminho());
            throw new EntityNotFoundException(FOTO_NAO_ENCONTRADA);
        }

        return new ConteudoFoto(arquivo, tipoDeConteudo(foto.getContentType()), foto.getNomeOriginal());
    }

    private MediaType tipoDeConteudo(String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record ConteudoFoto(Resource arquivo, MediaType contentType, String nomeOriginal) {}
}
