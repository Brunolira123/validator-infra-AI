package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
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

    /** Versão para a qual o vrip-updater deve atualizar o agente. Null = sem atualização pendente. */
    @Column(name = "versao_alvo_agente", length = 30)
    private String versaoAlvoAgente;

    /**
     * Última versão alvo cuja atualização falhou (FALHOU/ROLLBACK). O updater não tenta de novo a mesma versão
     * sozinho: só uma versão alvo diferente.
     */
    @Column(name = "versao_alvo_falhou", length = 30)
    private String versaoAlvoFalhou;

    /** Recebe as versões novas primeiro (escopo PILOTO). Default no banco para as linhas já existentes. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean piloto = false;

    @Column(name = "updater_versao", length = 30)
    private String updaterVersao;

    /** Última consulta do vrip-updater (processo separado do agente: não conta como contato do agente). */
    @Column(name = "updater_ultimo_contato")
    private Instant updaterUltimoContato;

    /** Já avisou no Discord que o agente está offline: no próximo contato sai o aviso de que voltou. */
    @Column(name = "alerta_offline_enviado", nullable = false)
    private Boolean alertaOfflineEnviado = false;

    // ---- Desativação (cliente que saiu da base ou parou de usar o agente). Null enquanto ativo. ----

    @Column(name = "desativado_em")
    private Instant desativadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desativado_por_id")
    private Usuario desativadoPor;

    @Column(name = "motivo_desativacao", length = 300)
    private String motivoDesativacao;

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
