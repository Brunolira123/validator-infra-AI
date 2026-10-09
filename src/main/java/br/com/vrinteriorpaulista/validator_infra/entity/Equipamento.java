package br.com.vrinteriorpaulista.validator_infra.entity;

import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "equipamento")
@Getter
@Setter
public class Equipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "levantamento_id", nullable = false)
    private Levantamento levantamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaEquipamento categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FuncaoEquipamento funcao;

    @Column(name = "sequencia", nullable = false)
    private Integer sequencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAnalise status = StatusAnalise.PENDENTE;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "equipamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Foto> fotos = new ArrayList<>();

    @OneToOne(mappedBy = "equipamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Analise analise;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }
}
