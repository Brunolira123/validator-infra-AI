package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SuporteClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.SuporteMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public SuporteMonitorController(SuporteMonitorService service) {
        this.service = service;
    }

    @Operation(summary = "Clientes ativos com situação do agente e containers",
            description = "busca: parte do nome (ignora acento e maiúscula). comParado: só quem tem container parado. offline: só agentes offline. ambiente = null se o agente nunca enviou.")
    @GetMapping("/clientes")
    public List<SuporteClienteResponse> listar(@RequestParam(required = false) String busca,
                                               @RequestParam(defaultValue = "false") boolean comParado,
                                               @RequestParam(defaultValue = "false") boolean offline) {
        return service.listar(busca, comParado, offline);
    }
}
