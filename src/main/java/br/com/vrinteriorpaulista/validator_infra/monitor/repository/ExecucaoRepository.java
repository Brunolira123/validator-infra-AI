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

    /** Resumo da última execução de cada cliente, sem carregar as entidades (lista única). */
    @Query("""
            select e.id as id, e.cliente.id as clienteId, e.recebidoEm as recebidoEm, e.ok as ok, e.erro as erro,
                   e.versaoBanco as versaoBanco, e.encerramentoAutomatico as encerramentoAutomatico,
                   e.consistenciaAutomatica as consistenciaAutomatica
            from Execucao e
            where e.id in (select max(x.id) from Execucao x group by x.cliente.id)
            """)
    List<UltimaExecucao> findResumoDaUltimaExecucao();

    /** Resultados (loja, tipo, ok) da última execução de cada cliente. */
    @Query("""
            select r.execucao.cliente.id as clienteId, r.lojaId as lojaId, r.tipo as tipo, r.ok as ok
            from ResultadoVerificacao r
            where r.execucao.id in (select max(x.id) from Execucao x group by x.cliente.id)
            """)
    List<ResultadoDaUltima> findResultadosDaUltimaExecucao();

    interface UltimaExecucao {
        Long getId();
        Long getClienteId();
        Instant getRecebidoEm();
        Boolean getOk();
        String getErro();
        String getVersaoBanco();
        Boolean getEncerramentoAutomatico();
        Boolean getConsistenciaAutomatica();
    }

    interface ResultadoDaUltima {
        Long getClienteId();
        Long getLojaId();
        String getTipo();
        Boolean getOk();
    }

    interface VersaoBancoCliente {
        Long getClienteId();
        String getVersaoBanco();
    }
}
