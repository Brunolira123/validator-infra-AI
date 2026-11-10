package br.com.vrinteriorpaulista.validator_infra.projetos.repository;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findAllByOrderByNomeAsc();

    List<Template> findByAtivoTrueOrderByNomeAsc();

    Optional<Template> findByNomeIgnoreCase(String nome);
}
