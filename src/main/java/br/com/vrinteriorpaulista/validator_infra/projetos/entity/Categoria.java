package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tipo de atividade com o esforço padrão (ex.: Treinamento, 12h). */
@Entity
@Table(name = "categoria", schema = "projetos",
        uniqueConstraints = @UniqueConstraint(name = "uk_projetos_categoria_nome", columnNames = "nome"))
@Getter
@Setter
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "horas_padrao", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasPadrao;

    @Column(nullable = false)
    private Boolean ativo = true;

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
