package br.com.vrinteriorpaulista.validator_infra.monitor.repository;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AtualizacaoAgente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AtualizacaoAgenteRepository extends JpaRepository<AtualizacaoAgente, Long> {

    /** A última atualização de cada cliente (uma consulta para a lista do Suporte). */
    @Query("""
            select a from AtualizacaoAgente a
            where a.id in (select max(x.id) from AtualizacaoAgente x group by x.cliente.id)
            """)
    List<AtualizacaoAgente> findUltimaDeCadaCliente();
}
