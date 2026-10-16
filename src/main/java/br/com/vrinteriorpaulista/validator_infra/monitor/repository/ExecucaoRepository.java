package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExecucaoRepository extends JpaRepository<Execucao, Long> {

    Optional<Execucao> findFirstByClienteIdOrderByIdDesc(Long clienteId);

    List<Execucao> findByClienteIdAndRecebidoEmAfterOrderByIdDesc(Long clienteId, Instant desde);
}
