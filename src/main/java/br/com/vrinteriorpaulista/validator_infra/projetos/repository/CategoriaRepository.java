package br.com.vrinteriorpaulista.validator_infra.projetos.repository;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findAllByOrderByNomeAsc();

    List<Categoria> findByAtivoTrueOrderByNomeAsc();

    Optional<Categoria> findByNomeIgnoreCase(String nome);
}
