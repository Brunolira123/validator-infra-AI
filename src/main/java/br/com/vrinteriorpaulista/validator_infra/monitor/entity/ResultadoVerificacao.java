package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Resultado de uma verificação numa loja. O tipo é texto livre (CONSISTENCIA, ENCERRAMENTO...) de propósito:
 * verificações novas no agente chegam sem mudar o contrato nem o schema.
 */
@Entity
@Table(name = "resultado", schema = "monitor")
@Getter
@Setter
public class ResultadoVerificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id")
    private Execucao execucao;

    /** Id da loja no banco VR do cliente. */
    @Column(name = "loja_id", nullable = false)
    private Long lojaId;

    @Column(name = "loja_cnpj", length = 20)
    private String lojaCnpj;

    @Column(name = "loja_descricao", length = 100)
    private String lojaDescricao;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false)
    private Boolean ok;

    /** Dia verificado (ex.: ontem para consistência, hoje para encerramento). */
    private LocalDate referencia;

    @Column(length = 500)
    private String detalhe;
}
