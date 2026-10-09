package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LevantamentoRepository extends JpaRepository<Levantamento, Long> {
    /**
     * Cliente e usuário vêm na mesma query: o LevantamentoResponse lê os dois (sem N+1).
     */
    @Query("""
            select l from Levantamento l
            join fetch l.cliente
            join fetch l.usuario
            where l.cliente.id = :clienteId
            order by l.criadoEm desc, l.id desc
            """)
    List<Levantamento> findByClienteIdOrderByCriadoEmDesc(@Param("clienteId") Long clienteId);

    @Query("""
            select l from Levantamento l
            join fetch l.cliente
            join fetch l.usuario
            order by l.criadoEm desc, l.id desc
            """)
    List<Levantamento> findAllOrderByCriadoEmDesc();

    List<Levantamento> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    /**
     * Carrega levantamento, cliente, usuário, equipamentos e análises numa única query (relatório sem N+1).
     */
    @Query("""
            select distinct l from Levantamento l
            join fetch l.cliente
            join fetch l.usuario
            left join fetch l.equipamentos e
            left join fetch e.analise
            where l.id = :id
            order by e.sequencia
            """)
    Optional<Levantamento> findByIdComEquipamentosEAnalises(@Param("id") Long id);
}
