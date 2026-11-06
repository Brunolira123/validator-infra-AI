package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ErroResponse;
import br.com.vrinteriorpaulista.validator_infra.controller.exception.GlobalExceptionHandler;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.AmbienteAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AgenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * Endpoints chamados pelo agente instalado no cliente ("Authorization: Agente &lt;token&gt;"). Só saem do cliente
 * para o servidor: o servidor nunca abre conexão com a rede do cliente.
 */
@RestController
@RequestMapping("/api/monitor/agente")
@Tag(name = "Monitoramento: agente", description = "Usado pelo agente instalado no cliente, com o token do cliente")
@Slf4j
public class AgenteController {

    static final String HEADER_VERSAO = "X-Agente-Versao";
    private static final String CAMINHO_AMBIENTE = "/ambiente";
    private static final int MAX_CAMPOS_NO_LOG = 10;
    private static final int MAX_MOTIVO_NO_LOG = 300;

    private final AgenteService agenteService;
    private final GlobalExceptionHandler errosGlobais;

    public AgenteController(AgenteService agenteService, GlobalExceptionHandler errosGlobais) {
        this.agenteService = agenteService;
        this.errosGlobais = errosGlobais;
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

    @Operation(summary = "Envia a foto do ambiente (containers Docker)", description = "Substitui a foto anterior do cliente. Até 100 containers. Também registra o contato.")
    @PostMapping("/ambiente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ambiente(@AuthenticationPrincipal AgenteAutenticado agente,
                         @RequestBody @Valid AmbienteAgenteRequest req) {
        agenteService.receberAmbiente(agente, req);
    }

    /**
     * Ambiente recusado: loga quem e por quê (o agente roda sem ninguém olhando) e responde como o resto da API.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        if (ehAmbiente(req)) {
            var erros = ex.getBindingResult().getFieldErrors();
            String campos = erros.stream()
                    .limit(MAX_CAMPOS_NO_LOG)
                    .map(f -> f.getField() + " (" + f.getDefaultMessage() + ")")
                    .collect(Collectors.joining(", "));
            if (erros.size() > MAX_CAMPOS_NO_LOG) {
                campos += " e mais " + (erros.size() - MAX_CAMPOS_NO_LOG);
            }
            log.warn("Ambiente recusado do agente {}: {}", agenteAtual(), campos);
        }
        return errosGlobais.validacao(ex, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ehAmbiente(req)) {
            String motivo = String.valueOf(ex.getMostSpecificCause().getMessage());
            log.warn("Ambiente recusado do agente {}: corpo inválido: {}", agenteAtual(),
                    motivo.length() > MAX_MOTIVO_NO_LOG ? motivo.substring(0, MAX_MOTIVO_NO_LOG) + "..." : motivo);
        }
        return errosGlobais.corpoInvalido(ex, req);
    }

    private static boolean ehAmbiente(HttpServletRequest req) {
        return req.getRequestURI().endsWith(CAMINHO_AMBIENTE);
    }

    private static String agenteAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AgenteAutenticado a
                ? "cliente " + a.clienteId() + " (" + a.nome() + ")"
                : "desconhecido";
    }
}
