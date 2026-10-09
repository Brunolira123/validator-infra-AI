package br.com.vrinteriorpaulista.validator_infra.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cliente")
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    @Column(length = 200)
    private String endereco;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(length = 20)
    private String telefone;

    @Column(length = 150)
    private String email;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Levantamento> levantamentos = new ArrayList<>();

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
