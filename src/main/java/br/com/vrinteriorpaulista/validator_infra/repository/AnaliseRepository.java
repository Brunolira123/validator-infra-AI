package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Analise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnaliseRepository extends JpaRepository<Analise, Long> {
    Optional<Analise> findByEquipamentoId(Long equipamentoId);
}
