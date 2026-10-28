package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Histórico de visitas: cada registro de visita grava uma linha; a última define o estado do prospect. */
@Entity
@Table(name = "prospect_visita", indexes = @Index(name = "idx_prospect_visita_prospect", columnList = "prospect_id, data_hora"))
@Getter
@Setter
public class VisitaProspect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prospect_id")
    private Prospect prospect;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoVisita resultado;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TemperaturaNegociacao temperatura;

    @Column(length = 500)
    private String observacao;

    /** Posição do celular no check-in (dado nosso, opcional). */
    private Double latitude;

    private Double longitude;
}
