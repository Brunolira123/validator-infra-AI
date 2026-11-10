package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** NACIONAL vale para todo planejamento; LOCAL só para clientes da UF (e da cidade, quando informada). */
@Entity
@Table(name = "feriado", schema = "projetos", indexes = @Index(name = "idx_projetos_feriado_data", columnList = "data"))
@Getter
@Setter
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 100)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EscopoFeriado escopo;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;
}
