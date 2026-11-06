package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Pedido do portal ao agente ("Consultar agora" ou log de container). O servidor não alcança a rede do cliente,
 * então o pedido fica na fila até o agente buscar (polling) e devolver o resultado.
 * <p>
 * Também é a auditoria dos pedidos de log: quem pediu, cliente, container e quando ficam; só o texto é apagado.
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

    /** Default no banco para as linhas criadas antes dos pedidos de log. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'CONSULTA'")
    private TipoPedido tipo = TipoPedido.CONSULTA;

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

    // ---- Só nos pedidos LOG ----

    @Column(length = 100)
    private String container;

    private Integer linhas;

    /** Apagado por LogContainerService.limparTextos depois da retenção; nunca vai para o log do servidor. */
    @Column(columnDefinition = "text")
    private String texto;

    /** O agente cortou o texto para caber no limite. */
    private Boolean truncado;

    @Column(length = 500)
    private String erro;

    @Column(name = "texto_apagado_em")
    private Instant textoApagadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = Instant.now();
    }

    public boolean aberto() {
        return status == StatusPedido.PENDENTE || status == StatusPedido.ENTREGUE;
    }
}
