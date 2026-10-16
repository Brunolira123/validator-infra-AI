package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.NfceNaoTransmitida;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ResultadoVerificacao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente com as lojas da última execução, cada uma com suas verificações.
 * {@code erro}: a última execução não conseguiu consultar o banco (aí {@code lojas} vem vazia).
 */
public record ClienteMonitoradoDetalheResponse(
        ClienteMonitoradoResponse cliente,
        String erro,
        List<Loja> lojas
) {
    public record Loja(
            Long lojaId,
            String cnpj,
            String descricao,
            boolean ok,
            List<Verificacao> verificacoes,
            /** NFC-e de ontem não transmitidas (só quando a consistência falha). */
            List<Nfce> nfceNaoTransmitidas
    ) {}

    public record Nfce(boolean contingencia, String motivoRejeicao) {}

    public record Verificacao(
            String tipo,
            boolean ok,
            LocalDate referencia,
            String detalhe
    ) {}

    public static List<Loja> lojas(Execucao execucao) {
        if (execucao == null) return List.of();

        Map<Long, List<ResultadoVerificacao>> porLoja = new LinkedHashMap<>();
        for (ResultadoVerificacao r : execucao.getResultados()) {
            porLoja.computeIfAbsent(r.getLojaId(), id -> new ArrayList<>()).add(r);
        }

        Map<Long, List<Nfce>> nfcePorLoja = new LinkedHashMap<>();
        for (NfceNaoTransmitida n : execucao.getNfceNaoTransmitidas()) {
            nfcePorLoja.computeIfAbsent(n.getLojaId(), id -> new ArrayList<>())
                    .add(new Nfce(n.getContingencia(), n.getMotivoRejeicao()));
        }

        return porLoja.values().stream().map(resultados -> {
            ResultadoVerificacao primeiro = resultados.get(0);
            List<Verificacao> verificacoes = resultados.stream()
                    .map(r -> new Verificacao(r.getTipo(), r.getOk(), r.getReferencia(), r.getDetalhe()))
                    .toList();
            return new Loja(primeiro.getLojaId(), primeiro.getLojaCnpj(), primeiro.getLojaDescricao(),
                    verificacoes.stream().allMatch(Verificacao::ok), verificacoes,
                    nfcePorLoja.getOrDefault(primeiro.getLojaId(), List.of()));
        }).toList();
    }
}
