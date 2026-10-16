package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoDetalheResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ExecucaoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.TokenAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.ClienteMonitoradoService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.PedidoConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints do portal (JWT de usuário). DIRETORIA entra como ADMIN pela hierarquia de papéis.
 */
@RestController
@RequestMapping("/api/monitor")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@Tag(name = "Monitoramento", description = "Clientes monitorados, resultados dos agentes e \"Consultar agora\"")
public class MonitorController {

    private final ClienteMonitoradoService clienteService;
    private final PedidoConsultaService pedidoService;

    public MonitorController(ClienteMonitoradoService clienteService, PedidoConsultaService pedidoService) {
        this.clienteService = clienteService;
        this.pedidoService = pedidoService;
    }

    @Operation(summary = "Lista os clientes monitorados com a situação atual", description = "Situação: AGUARDANDO (agente nunca enviou), OK, FALHA, OFFLINE (sem contato além do limite) ou INATIVO.")
    @GetMapping("/clientes")
    public List<ClienteMonitoradoResponse> listar() {
        return clienteService.listar();
    }

    @Operation(summary = "Cliente com as lojas e verificações da última execução")
    @GetMapping("/clientes/{id}")
    public ClienteMonitoradoDetalheResponse detalhar(@PathVariable Long id) {
        return clienteService.detalhar(id);
    }

    @Operation(summary = "Execuções dos últimos dias, mais recentes primeiro", description = "dias entre 1 e 90 (padrão 30).")
    @GetMapping("/clientes/{id}/historico")
    public List<ExecucaoResponse> historico(@PathVariable Long id, @RequestParam(defaultValue = "30") int dias) {
        return clienteService.historico(id, dias);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra um cliente monitorado", description = "Retorna o token do agente. É a única vez que ele aparece: vai no vr.properties (monitor.token).")
    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenAgenteResponse criar(@RequestBody @Valid ClienteMonitoradoRequest req) {
        return clienteService.criar(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gera um token novo para o agente", description = "O token anterior para de funcionar na hora.")
    @PostMapping("/clientes/{id}/token")
    public TokenAgenteResponse gerarNovoToken(@PathVariable Long id) {
        return clienteService.gerarNovoToken(id);
    }

    @Operation(summary = "Pede ao agente uma consulta agora", description = "O agente busca o pedido em até ~30s. Acompanhe por GET /api/monitor/pedidos/{id}. Se já houver pedido aberto para o cliente, devolve o mesmo.")
    @PostMapping("/clientes/{id}/consultar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PedidoResponse consultar(@PathVariable Long id) {
        return pedidoService.solicitar(id);
    }

    @Operation(summary = "Situação de um \"Consultar agora\"", description = "PENDENTE → ENTREGUE → CONCLUIDO (com execucaoId) ou EXPIRADO se o agente não responder.")
    @GetMapping("/pedidos/{id}")
    public PedidoResponse pedido(@PathVariable Long id) {
        return pedidoService.buscar(id);
    }
}
