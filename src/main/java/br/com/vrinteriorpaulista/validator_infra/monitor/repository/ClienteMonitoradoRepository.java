package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClienteMonitoradoRepository extends JpaRepository<ClienteMonitorado, Long> {

    Optional<ClienteMonitorado> findByTokenHashAndAtivoTrue(String tokenHash);

    List<ClienteMonitorado> findByAtivoTrueOrderByNomeAsc();

    /** Todos, com quem desativou já carregado (uma consulta para a lista única). */
    @Query("select c from ClienteMonitorado c left join fetch c.desativadoPor order by c.nome")
    List<ClienteMonitorado> findAllComDesativadoPor();

    List<ClienteMonitorado> findByAtivoTrueAndPilotoTrue();

    /** Agentes que já reportaram alguma vez e estão sem contato desde `limite`. */
    List<ClienteMonitorado> findByAtivoTrueAndUltimoContatoBefore(Instant limite);
}
