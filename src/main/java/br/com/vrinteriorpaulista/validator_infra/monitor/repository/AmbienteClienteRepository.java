package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmbienteClienteRepository extends JpaRepository<AmbienteCliente, Long> {
}
