package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Viagem prevista: ocupa a capacidade do analista no dia, sem consumir o contrato. */
@Entity
@Table(name = "deslocamento", schema = "projetos",
        indexes = @Index(name = "idx_projetos_deslocamento_planejamento", columnList = "planejamento_id"))
@Getter
@Setter
public class Deslocamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planejamento_id", nullable = false)
    private Planejamento planejamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal horas;

    @Column(length = 200)
    private String descricao;
}
