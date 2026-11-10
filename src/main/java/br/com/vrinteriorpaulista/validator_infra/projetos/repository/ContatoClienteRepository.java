package br.com.vrinteriorpaulista.validator_infra.projetos.repository;

import br.com.vrinteriorpaulista.validator_infra.projetos.entity.ContatoCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContatoClienteRepository extends JpaRepository<ContatoCliente, Long> {

    List<ContatoCliente> findByClienteIdOrderByNomeAsc(Long clienteId);

    List<ContatoCliente> findByClienteIdAndAtivoTrueOrderByNomeAsc(Long clienteId);
}
