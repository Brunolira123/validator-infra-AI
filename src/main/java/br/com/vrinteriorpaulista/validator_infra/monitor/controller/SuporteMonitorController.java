package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogCriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SuporteClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.LogContainerService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.SuporteMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tela de Suporte. DIRETORIA entra como ADMIN pela hierarquia de papéis.
 */
@RestController
@RequestMapping("/api/monitor/suporte")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@Tag(name = "Monitoramento: suporte", description = "Situação dos agentes e ambiente (containers) dos clientes")
public class SuporteMonitorController {

    private final SuporteMonitorService service;
    private final LogContainerService logService;

    public SuporteMonitorController(SuporteMonitorService service, LogContainerService logService) {
        this.service = service;
        this.logService = logService;
    }

    @Operation(summary = "Clientes ativos com situação do agente e containers",
            description = "busca: parte do nome (ignora acento e maiúscula). comParado: só quem tem container parado. offline: só agentes offline. ambiente = null se o agente nunca enviou.")
    @GetMapping("/clientes")
    public List<SuporteClienteResponse> listar(@RequestParam(required = false) String busca,
                                               @RequestParam(defaultValue = "false") boolean comParado,
                                               @RequestParam(defaultValue = "false") boolean offline) {
        return service.listar(busca, comParado, offline);
    }

    @Operation(summary = "Pede ao agente o log de um container",
            description = "linhas entre 1 e 500 (padrão 200). 404 se o container não está na última foto do ambiente; 409 se o agente está offline ou não suporta log (até 2.2.x). Acompanhe por GET .../log/{pedidoId}.")
    @PostMapping("/clientes/{id}/containers/{nome}/log")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PedidoLogCriadoResponse pedirLog(@PathVariable Long id, @PathVariable String nome,
                                            @RequestParam(defaultValue = "200") int linhas) {
        return logService.solicitar(id, nome, linhas);
    }

    @Operation(summary = "Situação e texto de um pedido de log",
            description = "PENDENTE → ENTREGUE → CONCLUIDO (com texto) ou ERRO; EXPIRADO se o agente não responder. O texto é apagado 1h depois de recebido.")
    @GetMapping("/clientes/{id}/containers/{nome}/log/{pedidoId}")
    public PedidoLogResponse log(@PathVariable Long id, @PathVariable String nome, @PathVariable Long pedidoId) {
        return logService.buscar(id, nome, pedidoId);
    }
}
