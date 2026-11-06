package br.com.vrinteriorpaulista.validator_infra.monitor.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ErroResponse;
import br.com.vrinteriorpaulista.validator_infra.controller.exception.GlobalExceptionHandler;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.AcaoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.AmbienteAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.LogAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.PedidoAgenteResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAgenteRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.ResultadoAtualizacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.monitor.dto.VersaoAlvoResponse;
import br.com.vrinteriorpaulista.validator_infra.monitor.security.AgenteAutenticado;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AcaoContainerService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AgenteService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AtualizacaoAgenteService;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.LogContainerService;
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
    static final String HEADER_VERSAO_UPDATER = "X-Updater-Versao";
    private static final String CAMINHO_AMBIENTE = "/ambiente";
    private static final int MAX_CAMPOS_NO_LOG = 10;
    private static final int MAX_MOTIVO_NO_LOG = 300;

    private final AgenteService agenteService;
    private final LogContainerService logService;
    private final AcaoContainerService acaoService;
    private final AtualizacaoAgenteService atualizacaoService;
    private final GlobalExceptionHandler errosGlobais;

    public AgenteController(AgenteService agenteService, LogContainerService logService,
                            AcaoContainerService acaoService, AtualizacaoAgenteService atualizacaoService,
                            GlobalExceptionHandler errosGlobais) {
        this.agenteService = agenteService;
        this.logService = logService;
        this.acaoService = acaoService;
        this.atualizacaoService = atualizacaoService;
        this.errosGlobais = errosGlobais;
    }

    @Operation(summary = "Polling do agente (a cada ~30s)", description = "200 com o pedido pendente; 204 se não houver. Agente 2.3.0+ recebe também tipo, container e linhas; os anteriores só recebem pedidoId e nunca pedidos LOG. START/RESTART só vão para o agente 2.4.0+. Também registra o contato (heartbeat).")
    @GetMapping("/pedidos")
    public ResponseEntity<PedidoAgenteResponse> pedidos(@AuthenticationPrincipal AgenteAutenticado agente,
                                                        @RequestHeader(value = HEADER_VERSAO, required = false) String versao) {
        return agenteService.buscarPedido(agente, versao)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Envia o log de um container (pedido LOG)", description = "texto até 256 KB (UTF-8), ou erro se não conseguiu ler. 400 se o pedido não for um LOG deste cliente; 409 se já foi respondido.")
    @PostMapping("/pedidos/{id}/log")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void log(@AuthenticationPrincipal AgenteAutenticado agente, @PathVariable Long id,
                    @RequestBody @Valid LogAgenteRequest req) {
        logService.receber(agente, id, req);
    }

    @Operation(summary = "Envia o resultado de um START/RESTART", description = "ok=true → CONCLUIDO; ok=false → ERRO com o erro informado. 400 se o pedido não for uma ação deste cliente; 409 se já foi respondido.")
    @PostMapping("/pedidos/{id}/acao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acao(@AuthenticationPrincipal AgenteAutenticado agente, @PathVariable Long id,
                     @RequestBody @Valid AcaoAgenteRequest req) {
        acaoService.receber(agente, id, req);
    }

    @Operation(summary = "vrip-updater: há versão do agente para instalar?", description = "200 com versaoAlvo, ou 204 sem pendência (sem alvo, agente já na versão, ou a versão já falhou neste cliente). Registra o contato do updater.")
    @GetMapping("/atualizacao")
    public ResponseEntity<VersaoAlvoResponse> atualizacao(@AuthenticationPrincipal AgenteAutenticado agente,
                                                          @RequestHeader(value = HEADER_VERSAO_UPDATER, required = false) String versaoUpdater) {
        return atualizacaoService.versaoPendente(agente, versaoUpdater)
                .map(v -> ResponseEntity.ok(new VersaoAlvoResponse(v)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "vrip-updater: resultado da atualização", description = "ATUALIZADO limpa a versão alvo; FALHOU/ROLLBACK mantém o alvo, mas essa versão não é oferecida de novo ao cliente.")
    @PostMapping("/atualizacao/resultado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resultadoAtualizacao(@AuthenticationPrincipal AgenteAutenticado agente,
                                     @RequestHeader(value = HEADER_VERSAO_UPDATER, required = false) String versaoUpdater,
                                     @RequestBody @Valid ResultadoAtualizacaoRequest req) {
        atualizacaoService.receberResultado(agente, versaoUpdater, req);
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
