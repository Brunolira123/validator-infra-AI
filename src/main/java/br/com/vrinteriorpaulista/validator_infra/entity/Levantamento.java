package br.com.vrinteriorpaulista.validator_infra.entity;

import br.com.vrinteriorpaulista.validator_infra.enums.StatusLevantamento;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "levantamento")
@SQLRestriction("excluido_em is null")
@Getter
@Setter
public class Levantamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnore
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "qtd_servidores", nullable = false)
    private Integer qtdServidores = 0;

    @Column(name = "qtd_pdvs", nullable = false)
    private Integer qtdPdvs = 0;

    @Column(name = "qtd_retaguardas", nullable = false)
    private Integer qtdRetaguardas = 0;

    @Column(name = "consulta_preco", nullable = false)
    private Boolean consultaPreco = false;

    @Column(name = "qtd_consulta_preco")
    private Integer qtdConsultaPreco = 0;

    @Column(length = 500)
    private String outros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusLevantamento status = StatusLevantamento.RASCUNHO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    /** Soft delete (só ADMIN). Registros arquivados somem de todas as consultas via @SQLRestriction. */
    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluido_por_id")
    @JsonIgnore
    private Usuario excluidoPor;

    @OneToMany(mappedBy = "levantamento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Equipamento> equipamentos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Aceita fotos e análises: RASCUNHO ou EM_ANALISE. CONCLUIDO e CANCELADO são somente leitura.
     */
    public boolean isEditavel() {
        return status == StatusLevantamento.RASCUNHO || status == StatusLevantamento.EM_ANALISE;
    }

    public void arquivar(Usuario usuario) {
        this.excluidoEm = LocalDateTime.now();
        this.excluidoPor = usuario;
    }
}
