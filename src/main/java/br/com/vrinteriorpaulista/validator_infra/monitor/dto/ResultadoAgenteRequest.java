package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.OrigemExecucao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * O que o agente envia depois de consultar o banco do cliente.
 * {@code lojas} pode vir vazia: o cliente não tem loja ativa a verificar (conta como execução ok).
 * {@code erro} preenchido = o agente não conseguiu consultar o banco (execução com falha, lojas vazias).
 */
public record ResultadoAgenteRequest(
        @NotNull OrigemExecucao origem,
        /** Preenchido quando a execução responde a um "Consultar agora". */
        Long pedidoId,
        /** Com fuso (ex.: 2026-10-15T07:00:00-03:00): o agente roda no horário do cliente. */
        @NotNull OffsetDateTime executadoEm,
        @Size(max = 30) String versaoAgente,
        @Size(max = 500) String erro,
        /** Versão do banco VR (uma só para todas as lojas). Agentes até a 2.0.2 não enviam. */
        @Size(max = 50) String versaoBanco,
        Boolean encerramentoAutomatico,
        Boolean consistenciaAutomatica,
        @NotNull @Valid List<Loja> lojas
) {
    public record Loja(
            @NotNull Long lojaId,
            @Size(max = 20) String cnpj,
            @Size(max = 100) String descricao,
            @NotEmpty @Valid List<Verificacao> verificacoes,
            /** Só quando a consistência falha: NFC-e de ontem não transmitidas. */
            @Valid List<NfceNaoTransmitida> nfceNaoTransmitidas
    ) {}

    public record NfceNaoTransmitida(
            boolean contingencia,
            /** Preenchido = rejeitada pela SEFAZ. */
            @Size(max = 500) String motivoRejeicao
    ) {}

    public record Verificacao(
            /** CONSISTENCIA, ENCERRAMENTO... Livre, para verificações novas não mudarem o contrato. */
            @NotBlank @Size(max = 50) String tipo,
            @NotNull Boolean ok,
            LocalDate referencia,
            @Size(max = 500) String detalhe
    ) {}
}
