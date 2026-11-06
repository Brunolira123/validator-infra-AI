package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SituacaoAgente;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SuporteClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.AmbienteCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Visão da tela de Suporte: um item por cliente ativo, com a situação do agente e o ambiente (containers).
 * Três consultas fixas, sem N+1; os filtros rodam em memória (poucas centenas de clientes).
 */
@Service
public class SuporteMonitorService {

    private final ClienteMonitoradoRepository clienteRepo;
    private final ExecucaoRepository execucaoRepo;
    private final AmbienteClienteRepository ambienteRepo;
    private final ClienteMonitoradoService clienteService;

    public SuporteMonitorService(ClienteMonitoradoRepository clienteRepo,
                                 ExecucaoRepository execucaoRepo,
                                 AmbienteClienteRepository ambienteRepo,
                                 ClienteMonitoradoService clienteService) {
        this.clienteRepo = clienteRepo;
        this.execucaoRepo = execucaoRepo;
        this.ambienteRepo = ambienteRepo;
        this.clienteService = clienteService;
    }

    @Transactional(readOnly = true)
    public List<SuporteClienteResponse> listar(String busca, boolean comParado, boolean offline) {
        Map<Long, String> versaoBanco = new HashMap<>();
        execucaoRepo.findVersaoBancoDaUltimaExecucao()
                .forEach(v -> versaoBanco.put(v.getClienteId(), v.getVersaoBanco()));
        Map<Long, AmbienteCliente> ambientes = ambienteRepo.findAll().stream()
                .collect(Collectors.toMap(AmbienteCliente::getId, Function.identity()));
        String termo = busca == null || busca.isBlank() ? null : normalizar(busca);

        return clienteRepo.findByAtivoTrueOrderByNomeAsc().stream()
                .filter(c -> termo == null || normalizar(c.getNome()).contains(termo))
                .map(c -> new SuporteClienteResponse(c.getId(), c.getNome(), situacao(c), c.getUltimoContato(),
                        versaoBanco.get(c.getId()), c.getVersaoAgente(),
                        SuporteClienteResponse.Ambiente.from(ambientes.get(c.getId()))))
                .filter(r -> !offline || r.situacao() == SituacaoAgente.OFFLINE)
                .filter(r -> !comParado || (r.ambiente() != null && r.ambiente().parados() > 0))
                .toList();
    }

    /** Mesma regra do monitoramento; agente que nunca fez contato também conta como offline. */
    private SituacaoAgente situacao(ClienteMonitorado c) {
        return c.getUltimoContato() == null || clienteService.semContato(c)
                ? SituacaoAgente.OFFLINE : SituacaoAgente.ONLINE;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
