package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClienteMonitoradoRepository extends JpaRepository<ClienteMonitorado, Long> {

    Optional<ClienteMonitorado> findByTokenHashAndAtivoTrue(String tokenHash);

    List<ClienteMonitorado> findAllByOrderByNomeAsc();

    /** Agentes que já reportaram alguma vez, estão sem contato desde `limite` e ainda não foram alertados. */
    List<ClienteMonitorado> findByAtivoTrueAndAlertaOfflineEnviadoFalseAndUltimoContatoBefore(Instant limite);
}
