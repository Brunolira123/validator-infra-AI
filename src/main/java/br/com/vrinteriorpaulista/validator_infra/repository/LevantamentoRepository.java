package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LevantamentoRepository extends JpaRepository<Levantamento, Long> {
    List<Levantamento> findByClienteIdOrderByCriadoEmDesc(Long clienteId);
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
