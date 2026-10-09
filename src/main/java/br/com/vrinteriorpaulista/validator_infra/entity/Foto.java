package br.com.vrinteriorpaulista.validator_infra.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "foto")
@SQLRestriction("excluido_em is null")
@Getter
@Setter
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipamento_id", nullable = false)
    @JsonIgnore
    private Equipamento equipamento;

    @Column(nullable = false, length = 500)
    private String caminho;

    @Column(name = "nome_original", length = 200)
    private String nomeOriginal;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(nullable = false)
    private Integer sequencia;

    @Column(length = 500)
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    /** Soft delete (só ADMIN). Registros arquivados somem de todas as consultas via @SQLRestriction. */
    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluido_por_id")
    @JsonIgnore
    private Usuario excluidoPor;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }

    public void arquivar(Usuario usuario) {
        this.excluidoEm = LocalDateTime.now();
        this.excluidoPor = usuario;
    }
}
