package br.com.vrinteriorpaulista.validator_infra.entity;

import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "requisito")
@Getter
@Setter
public class Requisito {

    public static final String VERSAO_VIGENTE = "2026.1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaEquipamento categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuncaoEquipamento funcao;

    @Column(nullable = false, length = 50)
    private String campo;  // ex: "ram_gb", "cpu_geracao", "so_nome"

    @Column(nullable = false, length = 10)
    private String operador;  // ">=", "<=", "==", "IN", "MATCHES"

    @Column(nullable = false, length = 200)
    private String valor;  // ex: "16", "10", "Windows 11"

    @Column(name = "condicao_extra", length = 500)
    private String condicaoExtra;  // ex: "ate 50 conexoes"

    @Column(nullable = false, length = 20)
    private String versao;  // ex: "2026.1"

    @Column(nullable = false)
    private Boolean vigente = true;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "vigente_ate")
    private LocalDateTime vigenteAte;

    @Column(length = 500)
    private String descricao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.vigenteDesde == null) {
            this.vigenteDesde = LocalDateTime.now();
        }
    }
}
