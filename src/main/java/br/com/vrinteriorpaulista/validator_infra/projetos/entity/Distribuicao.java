package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Parte das horas de uma atividade para um analista. A soma precisa fechar com as horas da atividade. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Distribuicao {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal horas;

    public Distribuicao(Usuario usuario, BigDecimal horas) {
        this.usuario = usuario;
        this.horas = horas;
    }
}
