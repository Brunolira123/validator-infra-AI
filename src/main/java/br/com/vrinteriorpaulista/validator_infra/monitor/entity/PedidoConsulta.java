package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * "Consultar agora" pedido no portal. O servidor não alcança a rede do cliente, então o pedido fica na fila
 * até o agente buscar (polling) e devolver o resultado.
 */
@Entity
@Table(name = "pedido", schema = "monitor")
@Getter
@Setter
public class PedidoConsulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteMonitorado cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status = StatusPedido.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitado_por_id")
    private Usuario solicitadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "entregue_em")
    private Instant entregueEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execucao_id")
    private Execucao execucao;

    @PrePersist
    public void prePersist() {
        this.criadoEm = Instant.now();
    }

    public boolean aberto() {
        return status == StatusPedido.PENDENTE || status == StatusPedido.ENTREGUE;
    }
}
