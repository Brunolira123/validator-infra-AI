package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.EquipamentoHomologado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipamentoHomologadoRepository extends JpaRepository<EquipamentoHomologado, Long> {
    List<EquipamentoHomologado> findByFabricanteIgnoreCaseAndModeloIgnoreCase(
            String fabricante, String modelo
    );

    /**
     * Query explícita: a derivada (Containing) gera "escape '\'", que o driver do Postgres
     * rejeita quando standard_conforming_strings está desligado.
     */
    @Query("""
            select h from EquipamentoHomologado h
            where lower(h.fabricante) = lower(:fabricante)
              and lower(h.modelo) like concat('%', lower(:modelo), '%')
            """)
    List<EquipamentoHomologado> findByFabricanteIgnoreCaseAndModeloContainingIgnoreCase(
            @Param("fabricante") String fabricante, @Param("modelo") String modelo
    );
    List<EquipamentoHomologado> findByFabricanteIgnoreCase(String fabricante);
}
