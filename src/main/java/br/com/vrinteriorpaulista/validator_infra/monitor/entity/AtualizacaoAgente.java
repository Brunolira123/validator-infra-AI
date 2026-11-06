package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Histórico das tentativas de atualização do agente, informadas pelo vrip-updater do cliente.
 */
@Entity
@Table(name = "atualizacao_agente", schema = "monitor",
        indexes = @Index(name = "idx_atualizacao_agente_cliente", columnList = "cliente_id, id"))
@Getter
@Setter
public class AtualizacaoAgente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteMonitorado cliente;

    @Column(name = "versao_anterior", length = 30)
    private String versaoAnterior;

    @Column(name = "versao_alvo", nullable = false, length = 30)
    private String versaoAlvo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAtualizacao status;

    @Column(length = 500)
    private String detalhe;

    /** Relógio do servidor. */
    @Column(nullable = false)
    private Instant em;
}
