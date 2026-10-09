package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByCnpj(String cnpj);
    boolean existsByCnpj(String cnpj);

    /**
     * Inclui arquivados (query nativa não passa pelo @SQLRestriction): o CNPJ é unique no banco,
     * então recadastrar um cliente arquivado precisa reaproveitar o registro.
     */
    @Query(value = "select * from cliente where cnpj = :cnpj", nativeQuery = true)
    Optional<Cliente> findByCnpjIncluindoArquivados(@Param("cnpj") String cnpj);
}
