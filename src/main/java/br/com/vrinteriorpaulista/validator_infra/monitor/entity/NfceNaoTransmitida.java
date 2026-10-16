package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * NFC-e do dia anterior que a loja não transmitiu. O agente só envia quando a consistência da loja falha:
 * é o que costuma segurar a consistência (nota em contingência ou rejeitada pela SEFAZ).
 */
@Entity
@Table(name = "nfce_nao_transmitida", schema = "monitor")
@Getter
@Setter
public class NfceNaoTransmitida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id")
    private Execucao execucao;

    /** Id da loja no banco VR do cliente. */
    @Column(name = "loja_id", nullable = false)
    private Long lojaId;

    @Column(nullable = false)
    private Boolean contingencia;

    /** Preenchido = rejeitada pela SEFAZ. */
    @Column(name = "motivo_rejeicao", length = 500)
    private String motivoRejeicao;
}
