package br.com.vrinteriorpaulista.validator_infra.projetos.repository;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    boolean existsByDataAndEscopo(LocalDate data, EscopoFeriado escopo);

    List<Feriado> findAllByOrderByDataAsc();
}
