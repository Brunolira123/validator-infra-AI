package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Foto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {
    List<Foto> findByEquipamentoIdOrderBySequenciaAsc(Long equipamentoId);

    /** Maior sequência incluindo arquivadas: a próxima foto nunca repete o número de uma arquivada. */
    @Query(value = "select coalesce(max(sequencia), 0) from foto where equipamento_id = :equipamentoId",
            nativeQuery = true)
    int maiorSequenciaIncluindoArquivadas(@Param("equipamentoId") Long equipamentoId);
}
