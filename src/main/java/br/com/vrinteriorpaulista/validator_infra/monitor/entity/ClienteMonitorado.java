package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cliente com o agente de monitoramento instalado. Não é o Cliente do comercial: são cadastros independentes.
 */
@Entity
@Table(name = "cliente", schema = "monitor")
@Getter
@Setter
public class ClienteMonitorado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 14)
    private String cnpj;

    /** SHA-256 (hex) do token do agente. O token em si só é exibido uma vez, quando é gerado. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Boolean ativo = true;

    /** Última requisição do agente (o polling de pedidos a cada 30s funciona como heartbeat). */
    @Column(name = "ultimo_contato")
    private Instant ultimoContato;

    @Column(name = "versao_agente", length = 30)
    private String versaoAgente;

    /** Já avisou no Discord que o agente está offline: no próximo contato sai o aviso de que voltou. */
    @Column(name = "alerta_offline_enviado", nullable = false)
    private Boolean alertaOfflineEnviado = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = Instant.now();
    }
}
