package br.com.vrinteriorpaulista.validator_infra.repository;

import br.com.vrinteriorpaulista.validator_infra.entity.Requisito;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequisitoRepository extends JpaRepository<Requisito, Long> {
    List<Requisito> findByCategoriaAndFuncaoAndVigenteTrue(
            CategoriaEquipamento categoria,
            FuncaoEquipamento funcao
    );
    List<Requisito> findByVersaoAndVigenteTrue(String versao);
}
