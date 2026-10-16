package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AgenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints chamados pelo agente instalado no cliente ("Authorization: Agente &lt;token&gt;"). Só saem do cliente
 * para o servidor: o servidor nunca abre conexão com a rede do cliente.
 */
@RestController
@RequestMapping("/api/monitor/agente")
@Tag(name = "Monitoramento: agente", description = "Usado pelo agente instalado no cliente, com o token do cliente")
public class AgenteController {

    static final String HEADER_VERSAO = "X-Agente-Versao";

    private final AgenteService agenteService;

    public AgenteController(AgenteService agenteService) {
        this.agenteService = agenteService;
    }

    @Operation(summary = "Polling do agente (a cada ~30s)", description = "200 com pedidoId se houver \"Consultar agora\" pendente; 204 se não. Também registra o contato (heartbeat).")
    @GetMapping("/pedidos")
    public ResponseEntity<PedidoAgenteResponse> pedidos(@AuthenticationPrincipal AgenteAutenticado agente,
                                                        @RequestHeader(value = HEADER_VERSAO, required = false) String versao) {
        return agenteService.buscarPedido(agente, versao)
                .map(p -> ResponseEntity.ok(new PedidoAgenteResponse(p.getId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Envia o resultado de uma execução", description = "Com pedidoId, conclui o \"Consultar agora\" (400 se o pedido não for deste cliente). Execuções AGENDADO geram alerta no Discord.")
    @PostMapping("/resultados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resultados(@AuthenticationPrincipal AgenteAutenticado agente,
                           @RequestBody @Valid ResultadoAgenteRequest req) {
        agenteService.receberResultado(agente, req);
    }
}
