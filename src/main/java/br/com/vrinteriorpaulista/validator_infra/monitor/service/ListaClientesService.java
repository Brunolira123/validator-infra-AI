package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteListaResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.FiltroClientes;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.OrdemVersao;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SuporteClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoBancoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository.ResultadoDaUltima;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository.UltimaExecucao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lista única de clientes monitorados (telas de Monitoramento e Suporte). Quatro consultas fixas (clientes com
 * quem desativou, última execução, resultados da última execução e ambientes), sem N+1; os filtros rodam em
 * memória (poucas centenas de clientes).
 */
@Service
public class ListaClientesService {

    private final ClienteMonitoradoRepository clienteRepo;
    private final ExecucaoRepository execucaoRepo;
    private final AmbienteClienteRepository ambienteRepo;
    private final ClienteMonitoradoService clienteService;
    private final ZoneId fuso;

    public ListaClientesService(ClienteMonitoradoRepository clienteRepo,
                                ExecucaoRepository execucaoRepo,
                                AmbienteClienteRepository ambienteRepo,
                                ClienteMonitoradoService clienteService,
                                @Value("${app.monitor.fuso:America/Sao_Paulo}") ZoneId fuso) {
        this.clienteRepo = clienteRepo;
        this.execucaoRepo = execucaoRepo;
        this.ambienteRepo = ambienteRepo;
        this.clienteService = clienteService;
        this.fuso = fuso;
    }

    @Transactional(readOnly = true)
    public List<ClienteListaResponse> listar(FiltroClientes f) {
        List<ClienteMonitorado> clientes = clienteRepo.findAllComDesativadoPor();
        Map<Long, UltimaExecucao> ultimas = execucaoRepo.findResumoDaUltimaExecucao().stream()
                .collect(Collectors.toMap(UltimaExecucao::getClienteId, Function.identity()));
        Map<Long, List<ResultadoDaUltima>> resultados = execucaoRepo.findResultadosDaUltimaExecucao().stream()
                .collect(Collectors.groupingBy(ResultadoDaUltima::getClienteId));
        Map<Long, AmbienteCliente> ambientes = ambienteRepo.findAll().stream()
                .collect(Collectors.toMap(AmbienteCliente::getId, Function.identity()));

        String maiorVersaoAgente = clientes.stream()
                .filter(ClienteMonitorado::getAtivo)
                .map(ClienteMonitorado::getVersaoAgente)
                .filter(Objects::nonNull)
                .max(OrdemVersao.COMPARADOR)
                .orElse(null);
        Instant inicioDeHoje = LocalDate.now(fuso).atStartOfDay(fuso).toInstant();
        String termo = f.busca() == null || f.busca().isBlank() ? null : SuporteMonitorService.normalizar(f.busca());
        Set<String> versoesBanco = f.versaoBanco() == null ? Set.of() : f.versaoBanco().stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        boolean filtraVersaoBanco = !versoesBanco.isEmpty() || f.semVersaoBanco();

        return clientes.stream()
                .filter(c -> f.inativos() || c.getAtivo())
                .filter(c -> termo == null || SuporteMonitorService.normalizar(c.getNome()).contains(termo))
                .filter(c -> f.piloto() == null || f.piloto().equals(c.getPiloto()))
                .filter(c -> !f.agenteDesatualizado() || desatualizado(c, maiorVersaoAgente))
                .map(c -> item(c, ultimas.get(c.getId()), resultados.getOrDefault(c.getId(), List.of()),
                        ambientes.get(c.getId()), inicioDeHoje))
                .filter(r -> f.situacao() == null || f.situacao().isEmpty() || f.situacao().contains(r.situacao()))
                .filter(r -> !f.comParado() || (r.ambiente() != null && r.ambiente().parados() > 0))
                .filter(r -> !filtraVersaoBanco || atendeVersaoBanco(r.versaoBanco(), versoesBanco, f.semVersaoBanco()))
                .toList();
    }

    /** Versões do banco VR dos clientes ativos (pela última execução), em ordem numérica. */
    @Transactional(readOnly = true)
    public List<VersaoBancoResponse> versoesBanco() {
        Set<Long> ativos = clienteRepo.findByAtivoTrueOrderByNomeAsc().stream()
                .map(ClienteMonitorado::getId)
                .collect(Collectors.toSet());
        Map<String, Long> contagem = new TreeMap<>(OrdemVersao.COMPARADOR);
        execucaoRepo.findVersaoBancoDaUltimaExecucao().stream()
                .filter(v -> ativos.contains(v.getClienteId()))
                .map(ExecucaoRepository.VersaoBancoCliente::getVersaoBanco)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .forEach(v -> contagem.merge(v, 1L, Long::sum));
        return contagem.entrySet().stream()
                .map(e -> new VersaoBancoResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private static boolean atendeVersaoBanco(String versao, Set<String> versoes, boolean semVersao) {
        if (versao == null || versao.isBlank()) return semVersao;
        return versoes.contains(versao.trim());
    }

    /** Agente que nunca informou a versão não entra: não dá para dizer que está atrás. */
    private static boolean desatualizado(ClienteMonitorado c, String maior) {
        return maior != null && c.getVersaoAgente() != null
                && OrdemVersao.comparar(c.getVersaoAgente(), maior) < 0;
    }

    private ClienteListaResponse item(ClienteMonitorado c, UltimaExecucao ultima, List<ResultadoDaUltima> resultados,
                                      AmbienteCliente ambiente, Instant inicioDeHoje) {
        long totalLojas = lojas(resultados, false);
        long lojasComFalha = lojas(resultados, true);

        return new ClienteListaResponse(
                c.getId(), c.getNome(), c.getCnpj(), c.getAtivo(),
                clienteService.situacao(c, ultima != null ? ultima.getOk() : null),
                c.getUltimoContato(), c.getVersaoAgente(),
                ultima != null ? ultima.getRecebidoEm() : null,
                totalLojas, lojasComFalha,
                ultima != null ? ultima.getVersaoBanco() : null,
                ultima != null ? ultima.getEncerramentoAutomatico() : null,
                ultima != null ? ultima.getConsistenciaAutomatica() : null,
                hoje(ultima, resultados, totalLojas, lojasComFalha, inicioDeHoje),
                ClienteListaResponse.Ambiente.from(ambiente),
                c.getVersaoAlvoAgente(), c.getPiloto(),
                SuporteClienteResponse.Updater.from(c),
                desativacao(c));
    }

    private static ClienteListaResponse.Hoje hoje(UltimaExecucao ultima, List<ResultadoDaUltima> resultados,
                                                  long totalLojas, long lojasComFalha, Instant inicioDeHoje) {
        if (ultima == null || ultima.getRecebidoEm().isBefore(inicioDeHoje)) return null;
        List<ClienteListaResponse.Verificacao> verificacoes = resultados.stream()
                .collect(Collectors.groupingBy(ResultadoDaUltima::getTipo, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new ClienteListaResponse.Verificacao(e.getKey(), lojas(e.getValue(), false),
                        lojas(e.getValue(), true)))
                .toList();
        return new ClienteListaResponse.Hoje(ultima.getRecebidoEm(), ultima.getOk(), ultima.getErro(),
                totalLojas, lojasComFalha, verificacoes);
    }

    private static long lojas(List<ResultadoDaUltima> resultados, boolean soComFalha) {
        return resultados.stream()
                .filter(r -> !soComFalha || !r.getOk())
                .map(ResultadoDaUltima::getLojaId)
                .distinct()
                .count();
    }

    private static ClienteListaResponse.Desativacao desativacao(ClienteMonitorado c) {
        if (c.getAtivo() || c.getDesativadoEm() == null) return null;
        Usuario por = c.getDesativadoPor();
        return new ClienteListaResponse.Desativacao(c.getDesativadoEm(),
                por != null ? por.getId() : null, por != null ? por.getNome() : null, c.getMotivoDesativacao());
    }
}
