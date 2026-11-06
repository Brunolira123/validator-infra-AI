package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExecucaoRepository extends JpaRepository<Execucao, Long> {

    Optional<Execucao> findFirstByClienteIdOrderByIdDesc(Long clienteId);

    List<Execucao> findByClienteIdAndRecebidoEmAfterOrderByIdDesc(Long clienteId, Instant desde);

    @Query("""
            select e.cliente.id as clienteId, e.versaoBanco as versaoBanco from Execucao e
            where e.id in (select max(x.id) from Execucao x group by x.cliente.id)
            """)
    List<VersaoBancoCliente> findVersaoBancoDaUltimaExecucao();

    interface VersaoBancoCliente {
        Long getClienteId();
        String getVersaoBanco();
    }
}
