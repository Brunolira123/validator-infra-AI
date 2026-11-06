package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface PedidoConsultaRepository extends JpaRepository<PedidoConsulta, Long> {

    /** Pedido aberto do mesmo tipo (e container, no LOG) para não enfileirar cliques repetidos. */
    @Query("""
            select p from PedidoConsulta p
            where p.cliente.id = :clienteId and p.tipo = :tipo and p.status in :status and p.criadoEm > :criadoDepoisDe
              and (:container is null or p.container = :container)
            order by p.id
            limit 1
            """)
    Optional<PedidoConsulta> findAberto(Long clienteId, TipoPedido tipo, String container,
                                        Collection<StatusPedido> status, Instant criadoDepoisDe);

    Optional<PedidoConsulta> findFirstByClienteIdAndStatusAndTipoInAndCriadoEmAfterOrderByIdAsc(
            Long clienteId, StatusPedido status, Collection<TipoPedido> tipos, Instant criadoDepoisDe);

    @Modifying
    @Query("""
            update PedidoConsulta p set p.texto = null, p.textoApagadoEm = :agora
            where p.tipo = br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido.LOG
              and p.texto is not null and p.concluidoEm < :limite
            """)
    int apagarTextosDeLog(Instant limite, Instant agora);
}
