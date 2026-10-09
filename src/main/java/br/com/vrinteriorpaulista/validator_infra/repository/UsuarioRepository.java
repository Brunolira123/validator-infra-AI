package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);
    Optional<Usuario> findByEmail(String email);
    boolean existsByLogin(String login);
    boolean existsByEmail(String email);
}
