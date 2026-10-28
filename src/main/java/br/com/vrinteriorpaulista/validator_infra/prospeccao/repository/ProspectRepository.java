package br.com.vrinteriorpaulista.validator_infra.prospeccao.repository;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProspectRepository extends JpaRepository<Prospect, Long>, JpaSpecificationExecutor<Prospect> {

    List<Prospect> findByPlaceIdIn(Collection<String> placeIds);

    /**
     * Inclui excluídos (query nativa não passa pelo @SQLRestriction): o placeId é unique no banco.
     * Trava a linha até o fim da transação: dois comerciais pegando o mesmo descartado não ganham os dois.
     */
    @Query(value = "select * from prospect where place_id = :placeId for update", nativeQuery = true)
    Optional<Prospect> findByPlaceIdIncluindoExcluidos(@Param("placeId") String placeId);

    @Override
    @EntityGraph(attributePaths = {"responsavel", "cliente"})
    Page<Prospect> findAll(Specification<Prospect> spec, Pageable pageable);
}
