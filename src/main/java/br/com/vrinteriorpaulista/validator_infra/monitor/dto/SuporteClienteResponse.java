package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AtualizacaoAgente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusAtualizacao;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record SuporteClienteResponse(
        Long id,
        String nome,
        SituacaoAgente situacao,
        Instant ultimoContato,
        /** Da última execução. */
        String versaoBanco,
        String versaoAgente,
        /** Null se o agente nunca enviou o ambiente. */
        Ambiente ambiente,
        /** Versão que o vrip-updater deve instalar; null = sem atualização pendente. */
        String versaoAlvoAgente,
        boolean piloto,
        /** Null se o vrip-updater nunca consultou o portal. */
        Updater updater,
        /** Null se nunca houve tentativa de atualização. */
        UltimaAtualizacao ultimaAtualizacao
) {
    public record Updater(String versao, Instant ultimoContato) {
        public static Updater from(ClienteMonitorado c) {
            return c.getUpdaterUltimoContato() == null ? null
                    : new Updater(c.getUpdaterVersao(), c.getUpdaterUltimoContato());
        }
    }

    public record UltimaAtualizacao(StatusAtualizacao status, String versaoAnterior, String versaoAlvo,
                                    String detalhe, Instant em) {
        public static UltimaAtualizacao from(AtualizacaoAgente a) {
            return a == null ? null
                    : new UltimaAtualizacao(a.getStatus(), a.getVersaoAnterior(), a.getVersaoAlvo(), a.getDetalhe(), a.getEm());
        }
    }

    public record Ambiente(
            Instant coletadoEm,
            Instant recebidoEm,
            boolean dockerDisponivel,
            String erro,
            int total,
            long rodando,
            long parados,
            /** Parados primeiro, depois por nome. */
            List<ContainerAmbiente> containers
    ) {
        private static final Comparator<ContainerAmbiente> ORDEM = Comparator
                .comparing(ContainerAmbiente::rodando)
                .thenComparing(ContainerAmbiente::nome, String.CASE_INSENSITIVE_ORDER);

        public static Ambiente from(AmbienteCliente a) {
            if (a == null) return null;
            List<ContainerAmbiente> containers = a.getContainers().stream().sorted(ORDEM).toList();
            long rodando = containers.stream().filter(ContainerAmbiente::rodando).count();
            return new Ambiente(a.getColetadoEm(), a.getRecebidoEm(), a.getDockerDisponivel(), a.getErro(),
                    containers.size(), rodando, containers.size() - rodando, containers);
        }
    }
}
