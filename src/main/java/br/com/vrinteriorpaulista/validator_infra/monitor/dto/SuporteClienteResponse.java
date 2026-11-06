package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;

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
        Ambiente ambiente
) {
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
