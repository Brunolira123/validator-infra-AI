package br.com.vrinteriorpaulista.validator_infra.entity;

import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusHomologacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipamento_homologado")
@Getter
@Setter
public class EquipamentoHomologado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fabricante;

    @Column(nullable = false, length = 150)
    private String modelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaEquipamento categoria;

    @Column(name = "tipo", length = 100)
    private String tipo;  // ex: "Torre", "SFF", "Rack"

    @Column(name = "sistema_operacional", length = 200)
    private String sistemaOperacional;  // ex: "Windows Server 2022", "Linux"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusHomologacao status;

    @Column(length = 1000)
    private String observacoes;

    @Column(length = 500)
    private String fonte;  // URL ou referência do documento

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
