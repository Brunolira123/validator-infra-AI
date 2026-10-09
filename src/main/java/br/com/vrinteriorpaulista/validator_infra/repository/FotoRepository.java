package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Foto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {
    List<Foto> findByEquipamentoIdOrderBySequenciaAsc(Long equipamentoId);
}
