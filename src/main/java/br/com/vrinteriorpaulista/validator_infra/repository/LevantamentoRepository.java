package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevantamentoRepository extends JpaRepository<Levantamento, Long> {
    List<Levantamento> findByClienteIdOrderByCriadoEmDesc(Long clienteId);
    List<Levantamento> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
}
