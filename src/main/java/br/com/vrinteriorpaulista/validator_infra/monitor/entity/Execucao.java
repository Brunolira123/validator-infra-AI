package br.com.vrinteriorpaulista.validator_infra.monitor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Um envio do agente: o resultado de todas as verificações, de todas as lojas, numa consulta ao banco do cliente.
 */
@Entity
@Table(name = "execucao", schema = "monitor",
        indexes = @Index(name = "idx_execucao_cliente", columnList = "cliente_id, recebido_em"))
@Getter
@Setter
public class Execucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private ClienteMonitorado cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemExecucao origem;

    /** Relógio do agente. */
    @Column(name = "executado_em", nullable = false)
    private Instant executadoEm;

    /** Relógio do servidor: é por ele que o histórico é ordenado e filtrado. */
    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    /** Todas as verificações de todas as lojas ok. */
    @Column(nullable = false)
    private Boolean ok;

    @Column(name = "versao_agente", length = 30)
    private String versaoAgente;

    /** O agente não conseguiu consultar o banco do cliente (ex.: banco fora do ar, senha trocada). */
    @Column(length = 500)
    private String erro;

    /** Versão do banco VR do cliente (uma só para todas as lojas). */
    @Column(name = "versao_banco", length = 50)
    private String versaoBanco;

    @Column(name = "encerramento_automatico")
    private Boolean encerramentoAutomatico;

    @Column(name = "consistencia_automatica")
    private Boolean consistenciaAutomatica;

    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lojaId, tipo")
    private List<ResultadoVerificacao> resultados = new ArrayList<>();

    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lojaId, id")
    private List<NfceNaoTransmitida> nfceNaoTransmitidas = new ArrayList<>();

    public void adicionar(ResultadoVerificacao resultado) {
        resultado.setExecucao(this);
        resultados.add(resultado);
    }

    public void adicionar(NfceNaoTransmitida nfce) {
        nfce.setExecucao(this);
        nfceNaoTransmitidas.add(nfce);
    }

    /** Lojas com pelo menos uma verificação falhando. */
    public long lojasComFalha() {
        return resultados.stream().filter(r -> !r.getOk()).map(ResultadoVerificacao::getLojaId).distinct().count();
    }

    public long totalLojas() {
        return resultados.stream().map(ResultadoVerificacao::getLojaId).distinct().count();
    }
}
