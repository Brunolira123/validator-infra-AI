package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** Data sem expediente só deste planejamento (feriado municipal não cadastrado, emenda...). */
@Entity
@Table(name = "feriado_extra", schema = "projetos",
        uniqueConstraints = @UniqueConstraint(name = "uk_projetos_feriado_extra",
                columnNames = {"planejamento_id", "data"}))
@Getter
@Setter
public class FeriadoExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planejamento_id", nullable = false)
    private Planejamento planejamento;

    @Column(nullable = false)
    private LocalDate data;

    @Column(length = 100)
    private String descricao;
}
