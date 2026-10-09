package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.dto.response.EquipamentoResponse;
import br.com.vrinteriorpaulista.validator_infra.entity.Equipamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {
    List<Equipamento> findByLevantamentoIdOrderBySequenciaAsc(Long levantamentoId);

    /**
     * Projeção escalar com a contagem de fotos numa única query. Não carrega a entidade Equipamento
     * de propósito: o @OneToOne(mappedBy) analise seria buscado um a um (N+1).
     */
    @Query("""
            select new br.com.vrinteriorpaulista.validator_infra.dto.response.EquipamentoResponse(
                e.id, e.categoria, e.funcao, e.sequencia, e.status, count(f))
            from Equipamento e
            left join e.fotos f
            where e.levantamento.id = :levantamentoId
            group by e.id, e.categoria, e.funcao, e.sequencia, e.status
            order by e.sequencia
            """)
    List<EquipamentoResponse> listarComQtdFotos(@Param("levantamentoId") Long levantamentoId);
}
