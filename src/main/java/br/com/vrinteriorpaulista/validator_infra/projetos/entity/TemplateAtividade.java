package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Atividade do template. Hierarquia e dependências por chave (texto), não por id, para o template ser escrito e
 * copiado inteiro. horas null = ao aplicar, usa as horas padrão da categoria.
 */
@Entity
@Table(name = "template_atividade", schema = "projetos",
        uniqueConstraints = @UniqueConstraint(name = "uk_projetos_template_atividade_chave",
                columnNames = {"template_id", "chave"}))
@Getter
@Setter
public class TemplateAtividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(nullable = false, length = 50)
    private String chave;

    @Column(name = "chave_pai", length = 50)
    private String chavePai;

    @Column(nullable = false, length = 200)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(precision = 7, scale = 2)
    private BigDecimal horas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depende_de", nullable = false, columnDefinition = "jsonb")
    private List<String> dependeDe = new ArrayList<>();

    @Column(nullable = false)
    private Boolean indivisivel = false;

    @Column(name = "do_cliente", nullable = false)
    private Boolean doCliente = false;

    @Column(name = "dias_cliente", nullable = false)
    private Integer diasCliente = 1;

    @Column(nullable = false)
    private Integer ordem = 0;
}
