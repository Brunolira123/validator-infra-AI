package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoAcaoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoCriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PilotoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoAlvoClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoAlvoLoteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoAlvoLoteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoLogResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.SuporteClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AcaoContainerService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AtualizacaoAgenteService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.LogContainerService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.SuporteMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tela de Suporte. DIRETORIA entra como ADMIN pela hierarquia de papéis. A atualização do agente (versão alvo
 * e piloto) é só ADMIN/DIRETORIA.
 */
@RestController
@RequestMapping("/api/monitor/suporte")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPORTE')")
@Tag(name = "Monitoramento: suporte", description = "Situação dos agentes e ambiente (containers) dos clientes")
public class SuporteMonitorController {

    private final SuporteMonitorService service;
    private final LogContainerService logService;
    private final AcaoContainerService acaoService;
    private final AtualizacaoAgenteService atualizacaoService;

    public SuporteMonitorController(SuporteMonitorService service, LogContainerService logService,
                                    AcaoContainerService acaoService, AtualizacaoAgenteService atualizacaoService) {
        this.service = service;
        this.logService = logService;
        this.acaoService = acaoService;
        this.atualizacaoService = atualizacaoService;
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
    public PedidoCriadoResponse pedirLog(@PathVariable Long id, @PathVariable String nome,
                                            @RequestParam(defaultValue = "200") int linhas) {
        return logService.solicitar(id, nome, linhas);
    }

    @Operation(summary = "Situação e texto de um pedido de log",
            description = "PENDENTE → ENTREGUE → CONCLUIDO (com texto) ou ERRO; EXPIRADO se o agente não responder. O texto é apagado 1h depois de recebido.")
    @GetMapping("/clientes/{id}/containers/{nome}/log/{pedidoId}")
    public PedidoLogResponse log(@PathVariable Long id, @PathVariable String nome, @PathVariable Long pedidoId) {
        return logService.buscar(id, nome, pedidoId);
    }

    @Operation(summary = "Pede ao agente para iniciar (START) ou reiniciar (RESTART) um container",
            description = "404 se o container não está na última foto do ambiente. 409 se o agente está offline ou é anterior à 2.4.0, se START com o container rodando ou RESTART com ele parado (pela última foto). Clique repetido devolve o pedido aberto.")
    @PostMapping("/clientes/{id}/containers/{nome}/acao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PedidoCriadoResponse pedirAcao(@PathVariable Long id, @PathVariable String nome,
                                          @RequestParam TipoPedido tipo) {
        return acaoService.solicitar(id, nome, tipo);
    }

    @Operation(summary = "Situação de um pedido de START/RESTART",
            description = "PENDENTE → ENTREGUE → CONCLUIDO ou ERRO (com o motivo); EXPIRADO se o agente não responder.")
    @GetMapping("/clientes/{id}/containers/{nome}/acao/{pedidoId}")
    public PedidoAcaoResponse acao(@PathVariable Long id, @PathVariable String nome, @PathVariable Long pedidoId) {
        return acaoService.buscar(id, nome, pedidoId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Define a versão alvo do agente de um cliente", description = "versao null cancela a atualização pendente. Formato 1.2.3 ou 1.2.3-rc.1.")
    @PutMapping("/clientes/{id}/agente/versao-alvo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definirVersaoAlvo(@PathVariable Long id, @RequestBody @Valid VersaoAlvoClienteRequest req) {
        atualizacaoService.definirVersaoAlvo(id, req.versao());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Define a versão alvo do agente em lote", description = "escopo PILOTO (clientes piloto), TODOS ou IDS (com ids). Só clientes ativos; quem já roda a versão fica de fora. Responde quantos foram marcados.")
    @PostMapping("/agente/versao-alvo")
    public VersaoAlvoLoteResponse definirVersaoAlvoEmLote(@RequestBody @Valid VersaoAlvoLoteRequest req) {
        return new VersaoAlvoLoteResponse(atualizacaoService.definirVersaoAlvoEmLote(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Inclui ou retira o cliente do piloto de atualização do agente")
    @PutMapping("/clientes/{id}/piloto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definirPiloto(@PathVariable Long id, @RequestBody @Valid PilotoRequest req) {
        atualizacaoService.definirPiloto(id, req.piloto());
    }
}
