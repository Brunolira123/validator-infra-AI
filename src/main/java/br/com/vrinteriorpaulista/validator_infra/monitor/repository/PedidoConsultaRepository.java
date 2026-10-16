package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface PedidoConsultaRepository extends JpaRepository<PedidoConsulta, Long> {

    Optional<PedidoConsulta> findFirstByClienteIdAndStatusInAndCriadoEmAfterOrderByIdAsc(
            Long clienteId, Collection<StatusPedido> status, Instant criadoDepoisDe);
}
