package br.com.vrinteriorpaulista.validator_infra.entity;

import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "analise")
@Getter
@Setter
public class Analise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipamento_id", nullable = false, unique = true)
    @JsonIgnore
    private Equipamento equipamento;

    // ---- Dados extraídos pela IA (pós-confirmação humana) ----
    @Column(length = 100)
    private String fabricante;

    @Column(length = 150)
    private String modelo;

    @Column(name = "cpu_fabricante", length = 50)
    private String cpuFabricante;

    @Column(name = "cpu_modelo", length = 100)
    private String cpuModelo;

    @Column(name = "cpu_geracao")
    private Integer cpuGeracao;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "cpu_threads")
    private Integer cpuThreads;

    @Column(name = "ram_gb")
    private Integer ramGb;

    @Column(name = "armazenamento_tipo", length = 20)
    private String armazenamentoTipo;

    @Column(name = "armazenamento_gb")
    private Integer armazenamentoGb;

    @Column(name = "so_nome", length = 100)
    private String soNome;

    @Column(name = "so_versao", length = 100)
    private String soVersao;

    // ---- Confiança por campo (0.0 a 1.0) ----
    @Column(name = "confianca_global")
    private Double confiancaGlobal;

    // ---- Resposta crua da IA (para auditoria e reprocessamento) ----
    @Column(name = "json_ia", columnDefinition = "TEXT")
    private String jsonIa;

    @Column(name = "json_ia_corrigido", columnDefinition = "TEXT")
    private String jsonIaCorrigido;

    // ---- Resultado do motor de regras ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAnalise resultado = StatusAnalise.PENDENTE;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // ---- Auditoria ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analisado_por_id")
    private Usuario analisadoPor;

    @Column(name = "analisado_em")
    private LocalDateTime analisadoEm;

    @Column(name = "versao_requisitos", length = 20)
    private String versaoRequisitos;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

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
