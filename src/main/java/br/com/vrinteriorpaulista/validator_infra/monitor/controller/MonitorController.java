package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteListaResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoDetalheResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ClienteMonitoradoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.DesativarClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ExecucaoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.FiltroClientes;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SituacaoCliente;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.TokenAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoBancoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.ClienteMonitoradoService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.ListaClientesService;
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
    private final ListaClientesService listaService;
    private final PedidoConsultaService pedidoService;

    public MonitorController(ClienteMonitoradoService clienteService, ListaClientesService listaService,
                             PedidoConsultaService pedidoService) {
        this.clienteService = clienteService;
        this.listaService = listaService;
        this.pedidoService = pedidoService;
    }

    @Operation(summary = "Lista única de clientes (Monitoramento + Suporte)",
            description = "Situação: AGUARDANDO (agente nunca enviou), OK, FALHA, OFFLINE (sem contato além do limite) ou INATIVO. "
                    + "Filtros combináveis: busca (nome, ignora acento e maiúscula), situacao (repetível), comParado, "
                    + "versaoBanco (repetível) e semVersaoBanco (juntos valem como OU), agenteDesatualizado (abaixo da maior "
                    + "versão em uso nos ativos), piloto, inativos (padrão false: só ativos; true inclui os desativados).")
    @GetMapping("/clientes")
    public List<ClienteListaResponse> listar(@RequestParam(required = false) String busca,
                                             @RequestParam(required = false) List<SituacaoCliente> situacao,
                                             @RequestParam(defaultValue = "false") boolean comParado,
                                             @RequestParam(required = false) List<String> versaoBanco,
                                             @RequestParam(defaultValue = "false") boolean semVersaoBanco,
                                             @RequestParam(defaultValue = "false") boolean agenteDesatualizado,
                                             @RequestParam(required = false) Boolean piloto,
                                             @RequestParam(defaultValue = "false") boolean inativos) {
        return listaService.listar(new FiltroClientes(busca, situacao, comParado, versaoBanco, semVersaoBanco,
                agenteDesatualizado, piloto, inativos));
    }

    @Operation(summary = "Versões do banco VR em uso nos clientes ativos, com a quantidade de clientes",
            description = "Pela última execução de cada cliente, em ordem numérica (4.4.93 antes de 4.4.100). Sem os clientes sem versão.")
    @GetMapping("/versoes-banco")
    public List<VersaoBancoResponse> versoesBanco() {
        return listaService.versoesBanco();
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

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desativa o cliente monitorado",
            description = "Revoga o token (agente e vrip-updater passam a receber 401), expira os pedidos abertos, cancela a "
                    + "versão alvo e tira o cliente dos alertas. O histórico fica. motivo opcional (até 300). 409 se já desativado.")
    @PostMapping("/clientes/{id}/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id, @RequestBody(required = false) @Valid DesativarClienteRequest req) {
        clienteService.desativar(id, req != null ? req.motivo() : null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reativa o cliente monitorado com um token novo",
            description = "Mesmo formato do POST /token: é a única vez que o token aparece. O token anterior à desativação segue recusado. 409 se já ativo.")
    @PostMapping("/clientes/{id}/reativar")
    public TokenAgenteResponse reativar(@PathVariable Long id) {
        return clienteService.reativar(id);
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
