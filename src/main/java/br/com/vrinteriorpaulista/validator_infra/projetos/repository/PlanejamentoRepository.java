package br.com.vrinteriorpaulista.validator_infra.projetos.repository;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Planejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.StatusProspect;
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

public interface PlanejamentoRepository extends JpaRepository<Planejamento, Long>, JpaSpecificationExecutor<Planejamento> {

    boolean existsByLevantamentoId(Long levantamentoId);

    @Override
    @EntityGraph(attributePaths = {"cliente", "responsavel"})
    Page<Planejamento> findAll(Specification<Planejamento> spec, Pageable pageable);

    /** Último cálculo dos planejamentos que ocupam agenda, menos o informado (0 = nenhum). */
    @Query("""
            select p.id as id, p.nome as nome, p.calculo as calculo from Planejamento p
            where p.situacao in :situacoes and p.calculo is not null
              and p.id <> :excluir
            """)
    List<CalculoSalvo> calculosQueOcupamAgenda(@Param("situacoes") Collection<SituacaoPlanejamento> situacoes,
                                               @Param("excluir") Long excluir);

    /** Clientes de contratos que o comercial fechou (prospect FECHADO ou LTO sob a responsabilidade dele). */
    @Query("""
            select distinct p.cliente.id from Prospect p
            where p.responsavel.id = :usuarioId and p.status in :status and p.cliente is not null
            """)
    List<Long> clientesFechadosPor(@Param("usuarioId") Long usuarioId,
                                   @Param("status") Collection<StatusProspect> status);

    interface CalculoSalvo {
        Long getId();

        String getNome();

        String getCalculo();
    }
}
