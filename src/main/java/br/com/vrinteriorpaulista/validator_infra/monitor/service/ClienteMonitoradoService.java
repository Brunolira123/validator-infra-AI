package br.com.vrinteriorpaulista.validator_infra.monitor.service;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoDetalheResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ExecucaoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SituacaoCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.TokenAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ExecucaoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.TokenAgente;
import br.com.vrinteriorpaulista.validator_infra.util.CnpjUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class ClienteMonitoradoService {

    private static final int MAX_DIAS_HISTORICO = 90;

    private final ClienteMonitoradoRepository repo;
    private final ExecucaoRepository execucaoRepo;
    private final Duration offlineApos;

    public ClienteMonitoradoService(ClienteMonitoradoRepository repo,
                                    ExecucaoRepository execucaoRepo,
                                    @Value("${app.monitor.offline-apos:PT2H}") Duration offlineApos) {
        this.repo = repo;
        this.execucaoRepo = execucaoRepo;
        this.offlineApos = offlineApos;
    }

    @Transactional
    public TokenAgenteResponse criar(ClienteMonitoradoRequest req) {
        String token = TokenAgente.gerar();

        ClienteMonitorado c = new ClienteMonitorado();
        c.setNome(req.nome().trim());
        c.setCnpj(req.cnpj() == null || req.cnpj().isBlank() ? null : CnpjUtils.normalizar(req.cnpj()));
        c.setTokenHash(TokenAgente.hash(token));
        repo.save(c);

        log.info("Cliente monitorado {} ({}) cadastrado", c.getId(), c.getNome());
        return new TokenAgenteResponse(resumo(c, null), token);
    }

    /** Invalida o token atual na hora: o agente para até receber o novo no vr.properties. */
    @Transactional
    public TokenAgenteResponse gerarNovoToken(Long id) {
        ClienteMonitorado c = buscar(id);
        String token = TokenAgente.gerar();
        c.setTokenHash(TokenAgente.hash(token));

        log.info("Token do agente do cliente monitorado {} ({}) trocado", c.getId(), c.getNome());
        return new TokenAgenteResponse(resumo(c, ultimaExecucao(id)), token);
    }

    @Transactional(readOnly = true)
    public List<ClienteMonitoradoResponse> listar() {
        return repo.findAllByOrderByNomeAsc().stream()
                .map(c -> resumo(c, ultimaExecucao(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteMonitoradoDetalheResponse detalhar(Long id) {
        ClienteMonitorado c = buscar(id);
        Execucao ultima = ultimaExecucao(id);
        return new ClienteMonitoradoDetalheResponse(resumo(c, ultima), ultima != null ? ultima.getErro() : null,
                ClienteMonitoradoDetalheResponse.lojas(ultima));
    }

    @Transactional(readOnly = true)
    public List<ExecucaoResponse> historico(Long id, int dias) {
        buscar(id);
        if (dias < 1 || dias > MAX_DIAS_HISTORICO) {
            throw new IllegalArgumentException("dias deve estar entre 1 e " + MAX_DIAS_HISTORICO);
        }
        Instant desde = Instant.now().minus(Duration.ofDays(dias));
        return execucaoRepo.findByClienteIdAndRecebidoEmAfterOrderByIdDesc(id, desde).stream()
                .map(ExecucaoResponse::from)
                .toList();
    }

    public ClienteMonitorado buscar(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente monitorado não encontrado"));
    }

    private Execucao ultimaExecucao(Long clienteId) {
        return execucaoRepo.findFirstByClienteIdOrderByIdDesc(clienteId).orElse(null);
    }

    private ClienteMonitoradoResponse resumo(ClienteMonitorado c, Execucao ultima) {
        return new ClienteMonitoradoResponse(
                c.getId(), c.getNome(), c.getCnpj(), c.getAtivo(),
                situacao(c, ultima),
                c.getUltimoContato(), c.getVersaoAgente(),
                ultima != null ? ultima.getRecebidoEm() : null,
                ultima != null ? ultima.totalLojas() : 0,
                ultima != null ? ultima.lojasComFalha() : 0
        );
    }

    private SituacaoCliente situacao(ClienteMonitorado c, Execucao ultima) {
        if (!c.getAtivo()) return SituacaoCliente.INATIVO;
        if (c.getUltimoContato() != null && c.getUltimoContato().isBefore(Instant.now().minus(offlineApos))) {
            return SituacaoCliente.OFFLINE;
        }
        if (ultima == null) return SituacaoCliente.AGUARDANDO;
        return ultima.getOk() ? SituacaoCliente.OK : SituacaoCliente.FALHA;
    }
}
