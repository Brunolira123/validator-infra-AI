package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Última foto do ambiente do cliente (containers Docker) enviada pelo agente. Uma linha por cliente, substituída
 * a cada envio: não guarda histórico. A chave é o próprio id do cliente.
 */
@Entity
@Table(name = "ambiente_cliente", schema = "monitor")
@Getter
@Setter
public class AmbienteCliente {

    @Id
    @Column(name = "cliente_monitorado_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_monitorado_id")
    private ClienteMonitorado cliente;

    /** Relógio do agente. */
    @Column(name = "coletado_em", nullable = false)
    private Instant coletadoEm;

    /** Relógio do servidor. */
    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Column(name = "docker_disponivel", nullable = false)
    private Boolean dockerDisponivel;

    /** O agente não conseguiu listar os containers (ex.: sem permissão no socket do Docker). */
    @Column(length = 500)
    private String erro;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ContainerAmbiente> containers = new ArrayList<>();
}
