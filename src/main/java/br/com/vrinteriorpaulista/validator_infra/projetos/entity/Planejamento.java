package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.entity.Levantamento;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Planejamento da implantação de um cliente (só planejamento: a execução é no Artia).
 * O template é copiado (nome e versão guardados como texto), não referenciado: mudar o template depois não
 * mexe no planejamento. O último cálculo fica em JSON; qualquer edição o marca como desatualizado.
 */
@Entity
@Table(name = "planejamento", schema = "projetos",
        indexes = {
                @Index(name = "idx_projetos_planejamento_cliente", columnList = "cliente_id"),
                @Index(name = "idx_projetos_planejamento_situacao", columnList = "situacao")
        })
@Getter
@Setter
public class Planejamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** O do LTO, quando o planejamento nasceu dele. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "levantamento_id")
    private Levantamento levantamento;

    @Column(nullable = false, length = 200)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoPlanejamento situacao = SituacaoPlanejamento.RASCUNHO;

    @Column(name = "template_nome", length = 150)
    private String templateNome;

    @Column(name = "template_versao")
    private Integer templateVersao;

    private LocalDate kickoff;

    @Column(name = "horas_contratadas", precision = 9, scale = 2)
    private BigDecimal horasContratadas;

    @Column(name = "custo_hora_servico", precision = 10, scale = 2)
    private BigDecimal custoHoraServico;

    @Column(name = "custo_hora_viagem", precision = 10, scale = 2)
    private BigDecimal custoHoraViagem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(length = 1000)
    private String observacao;

    @OneToMany(mappedBy = "planejamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem, id")
    private List<PlanejamentoAtividade> atividades = new ArrayList<>();

    @OneToMany(mappedBy = "planejamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("data, id")
    private List<Deslocamento> deslocamentos = new ArrayList<>();

    @OneToMany(mappedBy = "planejamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("data, id")
    private List<FeriadoExtra> feriadosExtras = new ArrayList<>();

    /** Último cálculo (CalculoResponse em JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String calculo;

    @Column(name = "calculado_em")
    private LocalDateTime calculadoEm;

    @Column(name = "calculo_desatualizado", nullable = false)
    private Boolean calculoDesatualizado = false;

    /** Cópia do início e do término do último cálculo, para a listagem. */
    @Column(name = "inicio_calculado")
    private LocalDate inicioCalculado;

    @Column(name = "termino_calculado")
    private LocalDate terminoCalculado;

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

    /** Chamado em toda edição que muda o resultado do cálculo. */
    public void marcarAlterado() {
        if (calculo != null) {
            calculoDesatualizado = true;
        }
    }
}
