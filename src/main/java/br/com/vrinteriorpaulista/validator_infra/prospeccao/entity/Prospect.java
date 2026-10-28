package br.com.vrinteriorpaulista.validator_infra.prospeccao.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Lugar que o comercial vai trabalhar em campo. Não é Cliente porque o Google não traz CNPJ: só vira Cliente no LTO.
 * <p>
 * Termos do Google: do Google, só o placeId pode ficar guardado. Nome, endereço, coordenadas e foto são buscados
 * de novo na hora de exibir (Place Details com cache de 24h). Cidade, UF e categoria são dados nossos (o que o
 * comercial buscou).
 */
@Entity
@Table(name = "prospect",
        uniqueConstraints = @UniqueConstraint(name = "uk_prospect_origem_id_externo", columnNames = {"origem", "id_externo"}),
        indexes = @Index(name = "idx_prospect_responsavel", columnList = "responsavel_id, status"))
@SQLRestriction("excluido_em is null")
@Getter
@Setter
public class Prospect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemProspect origem;

    /** Nulo para prospects que não vêm do Google (ex.: importados do CRM). */
    @Column(name = "place_id", unique = true, length = 300)
    private String placeId;

    /** ID no sistema de origem (CRM). Único junto com a origem. */
    @Column(name = "id_externo", length = 100)
    private String idExterno;

    /** Só para origem MANUAL/CRM. Nunca guardar o nome que veio do Google. */
    @Column(length = 200)
    private String nome;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CategoriaProspeccao categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProspect status = StatusProspect.NOVO;

    /** Da última visita. */
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado_visita", length = 20)
    private ResultadoVisita resultadoVisita;

    /** Da última visita; só quando o cliente atendeu. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TemperaturaNegociacao temperatura;

    @Column(name = "visitado_em")
    private LocalDateTime visitadoEm;

    @Column(length = 500)
    private String observacao;

    /** Quem salvou. COMERCIAL só enxerga os seus. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    /** Preenchido no LTO. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    /** Soft delete. Registros excluídos somem de todas as consultas via @SQLRestriction. */
    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
