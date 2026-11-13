package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ContainerAmbiente;

import java.time.Instant;
import java.util.List;

/**
 * Item da lista única de clientes (Monitoramento + Suporte). Os campos de id até consistenciaAutomatica são os
 * do ClienteMonitoradoResponse, na mesma forma, para a tela antiga continuar funcionando até o front migrar.
 */
public record ClienteListaResponse(
        Long id,
        String nome,
        String cnpj,
        boolean ativo,
        SituacaoCliente situacao,
        Instant ultimoContato,
        String versaoAgente,
        /** Recebimento da última execução (agendada ou sob demanda). */
        Instant ultimaExecucaoEm,
        long totalLojas,
        long lojasComFalha,
        /** Da última execução. */
        String versaoBanco,
        Boolean encerramentoAutomatico,
        Boolean consistenciaAutomatica,
        /** Verificações da última execução, se ela chegou hoje (fuso app.monitor.fuso); null se não houve. */
        Hoje hoje,
        /** Null se o agente nunca enviou o ambiente. */
        Ambiente ambiente,
        String versaoAlvoAgente,
        boolean piloto,
        /** Null se o vrip-updater nunca consultou o portal. */
        SuporteClienteResponse.Updater updater,
        /** Null enquanto o cliente está ativo. */
        Desativacao desativacao
) {
    public record Hoje(Instant execucaoEm, boolean ok, String erro, long totalLojas, long lojasComFalha,
                       /** Por tipo de verificação, em ordem alfabética. */
                       List<Verificacao> verificacoes) {}

    /** lojas: quantas lojas rodaram essa verificação; falhas: em quantas ela falhou. */
    public record Verificacao(String tipo, long lojas, long falhas) {}

    public record Ambiente(Instant coletadoEm, boolean dockerDisponivel, String erro,
                           int total, long rodando, long parados) {
        public static Ambiente from(AmbienteCliente a) {
            if (a == null) return null;
            long rodando = a.getContainers().stream().filter(ContainerAmbiente::rodando).count();
            int total = a.getContainers().size();
            return new Ambiente(a.getColetadoEm(), a.getDockerDisponivel(), a.getErro(), total, rodando, total - rodando);
        }
    }

    public record Desativacao(Instant em, Long porId, String porNome, String motivo) {}
}
