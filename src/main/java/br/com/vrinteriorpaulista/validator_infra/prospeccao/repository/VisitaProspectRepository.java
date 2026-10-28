package br.com.vrinteriorpaulista.validator_infra.prospeccao.repository;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.VisitaProspect;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitaProspectRepository extends JpaRepository<VisitaProspect, Long> {
}
