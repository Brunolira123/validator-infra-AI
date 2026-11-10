package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Atividade do planejamento. Com filhos, é resumo: as horas próprias são ignoradas no cálculo.
 * Atividade do cliente (doCliente) não usa analista: ocupa diasCliente dias úteis seguidos.
 */
@Entity
@Table(name = "planejamento_atividade", schema = "projetos",
        indexes = @Index(name = "idx_projetos_atividade_planejamento", columnList = "planejamento_id"))
@Getter
@Setter
public class PlanejamentoAtividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planejamento_id", nullable = false)
    private Planejamento planejamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pai_id")
    private PlanejamentoAtividade pai;

    @Column(nullable = false, length = 200)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal horas = BigDecimal.ZERO;

    /** Recebe todas as horas quando não há distribuição. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(nullable = false)
    private Boolean indivisivel = false;

    @Column(name = "do_cliente", nullable = false)
    private Boolean doCliente = false;

    @Column(name = "dias_cliente", nullable = false)
    private Integer diasCliente = 1;

    /** Só quando doCliente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contato_cliente_id")
    private ContatoCliente contatoCliente;

    @Column(nullable = false)
    private Integer ordem = 0;

    @ManyToMany
    @JoinTable(name = "planejamento_dependencia", schema = "projetos",
            joinColumns = @JoinColumn(name = "atividade_id"),
            inverseJoinColumns = @JoinColumn(name = "depende_de_id"))
    private Set<PlanejamentoAtividade> dependeDe = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "planejamento_distribuicao", schema = "projetos",
            joinColumns = @JoinColumn(name = "atividade_id"))
    @OrderColumn(name = "posicao")
    private List<Distribuicao> distribuicao = new ArrayList<>();
}
