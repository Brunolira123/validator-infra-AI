package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Equipamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {
    List<Equipamento> findByLevantamentoIdOrderBySequenciaAsc(Long levantamentoId);
}
