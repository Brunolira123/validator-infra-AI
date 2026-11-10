package br.com.vrinteriorpaulista.validator_infra.projetos.controller;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.*;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.CalculoPlanejamentoService;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.CronogramaPdfService;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.PlanejamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Planejamento de implantação. Leitura: PROJETOS, ADMIN, DIRETORIA e COMERCIAL (este só dos clientes cujo
 * contrato fechou; os outros respondem 404). Escrita e carga: só PROJETOS, ADMIN e DIRETORIA.
 */
@RestController
@RequestMapping("/api/projetos")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJETOS', 'COMERCIAL')")
@Tag(name = "Projetos: planejamento", description = "Planejamento de implantação (a execução segue no Artia)")
public class PlanejamentoController {

    private static final String EDITORES = "hasAnyRole('ADMIN', 'PROJETOS')";

    private final PlanejamentoService service;
    private final CalculoPlanejamentoService calculos;
    private final CronogramaPdfService cronogramas;

    public PlanejamentoController(PlanejamentoService service, CalculoPlanejamentoService calculos,
                                  CronogramaPdfService cronogramas) {
        this.service = service;
        this.calculos = calculos;
        this.cronogramas = cronogramas;
    }

    @Operation(summary = "Lista os planejamentos",
            description = "Filtros opcionais: situacao, responsavelId e busca (nome do planejamento ou do cliente). "
                    + "Paginada (pagina começa em 0, tamanho até 50), mais recentes primeiro.")
    @GetMapping("/planejamentos")
    public PaginaPlanejamentosResponse listar(@RequestParam(required = false) SituacaoPlanejamento situacao,
                                              @RequestParam(required = false) Long responsavelId,
                                              @RequestParam(required = false) @Size(max = 100) String busca,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(situacao, responsavelId, busca, pagina, tamanho);
    }

    @Operation(summary = "Planejamento com atividades, deslocamentos, feriados extras e o último cálculo")
    @GetMapping("/planejamentos/{id}")
    public PlanejamentoResponse detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @Operation(summary = "Cria um planejamento avulso (RASCUNHO)",
            description = "O LTO já cria um automaticamente. levantamentoId, se vier, precisa ser do mesmo cliente.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanejamentoResponse criar(@RequestBody @Valid PlanejamentoCriacaoRequest req) {
        return service.criar(req);
    }

    @Operation(summary = "Atualiza os dados do planejamento",
            description = "Substitui nome, kickoff, horas contratadas, custos/h, responsável, observação e levantamento. "
                    + "409 se CRIADO_ARTIA ou CANCELADO. Marca o cálculo como desatualizado.")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}")
    public PlanejamentoResponse atualizar(@PathVariable Long id, @RequestBody @Valid PlanejamentoDadosRequest req) {
        return service.atualizarDados(id, req);
    }

    @Operation(summary = "Muda a situação",
            description = "RASCUNHO→EM_PLANEJAMENTO|CANCELADO; EM_PLANEJAMENTO→ENVIADO|CANCELADO; "
                    + "ENVIADO→EM_PLANEJAMENTO|CRIADO_ARTIA|CANCELADO; CRIADO_ARTIA→CANCELADO; CANCELADO→EM_PLANEJAMENTO. "
                    + "ENVIADO exige cálculo atualizado. Outra transição: 409.")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}/situacao")
    public PlanejamentoResponse mudarSituacao(@PathVariable Long id, @RequestBody @Valid SituacaoRequest req) {
        return service.mudarSituacao(id, req);
    }

    @Operation(summary = "Aplica um template",
            description = "Copia as atividades com hierarquia e dependências, horas do template (ou da categoria) e "
                    + "responsáveis vazios; a situação vai para EM_PLANEJAMENTO. Só em RASCUNHO ou EM_PLANEJAMENTO. "
                    + "Com atividades já cadastradas, 409, a menos que substituir=true.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/aplicar-template")
    public PlanejamentoResponse aplicarTemplate(@PathVariable Long id, @RequestBody @Valid AplicarTemplateRequest req) {
        return service.aplicarTemplate(id, req);
    }

    @Operation(summary = "Salva o planejamento como template",
            description = "Com templateId: nova versão desse template (200). Sem: template novo com o nome (201; "
                    + "409 se o nome já existe). Funciona em qualquer situação.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/salvar-como-template")
    public ResponseEntity<TemplateResponse> salvarComoTemplate(@PathVariable Long id,
                                                               @RequestBody @Valid SalvarComoTemplateRequest req) {
        PlanejamentoService.Salvo salvo = service.salvarComoTemplate(id, req);
        return ResponseEntity.status(salvo.criado() ? HttpStatus.CREATED : HttpStatus.OK).body(salvo.template());
    }

    @Operation(summary = "Calcula o cronograma e salva como último cálculo",
            description = "Desconta a agenda dos analistas nos outros planejamentos EM_PLANEJAMENTO, ENVIADO e "
                    + "CRIADO_ARTIA e lista os conflitos. 400 com { erros: [...] } listando todos os problemas "
                    + "(atividade sem responsável, divisão que não soma, ciclo...). 409 se CRIADO_ARTIA ou CANCELADO.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/calcular")
    public CalculoResponse calcular(@PathVariable Long id) {
        return calculos.calcular(id);
    }

    @Operation(summary = "PDF do cronograma para o cliente",
            description = "Etapas, quem faz (VR ou o cliente) e datas do último cálculo; etapas do cliente em negrito. "
                    + "Nunca leva horas, custos, analistas nem observações internas. 409 sem cálculo ou com cálculo "
                    + "desatualizado. marcarEnviado=true passa a situação para ENVIADO (só PROJETOS/ADMIN/DIRETORIA, "
                    + "a partir de EM_PLANEJAMENTO; senão 403/409).")
    @GetMapping("/planejamentos/{id}/cronograma.pdf")
    public ResponseEntity<byte[]> cronogramaPdf(@PathVariable Long id,
                                                @RequestParam(defaultValue = "false") boolean marcarEnviado) {
        CronogramaPdfService.Pdf pdf = cronogramas.gerar(id, marcarEnviado);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.nomeArquivo()).build().toString())
                .body(pdf.conteudo());
    }

    // ----- atividades -----

    @Operation(summary = "Cria uma atividade",
            description = "horas null com categoria = horas padrão da categoria. paiId: atividade principal do mesmo "
                    + "planejamento. contatoClienteId só com doCliente.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/atividades")
    @ResponseStatus(HttpStatus.CREATED)
    public AtividadeResponse criarAtividade(@PathVariable Long id, @RequestBody @Valid AtividadeRequest req) {
        return service.criarAtividade(id, req);
    }

    @Operation(summary = "Atualiza uma atividade",
            description = "400 se o pai for a própria atividade ou uma subtarefa dela.")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}/atividades/{atividadeId}")
    public AtividadeResponse atualizarAtividade(@PathVariable Long id, @PathVariable Long atividadeId,
                                                @RequestBody @Valid AtividadeRequest req) {
        return service.atualizarAtividade(id, atividadeId, req);
    }

    @Operation(summary = "Apaga a atividade e as subtarefas dela")
    @PreAuthorize(EDITORES)
    @DeleteMapping("/planejamentos/{id}/atividades/{atividadeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAtividade(@PathVariable Long id, @PathVariable Long atividadeId) {
        service.excluirAtividade(id, atividadeId);
    }

    @Operation(summary = "Define a distribuição das horas entre analistas",
            description = "Substitui a lista; vazia = tudo para o responsável. A soma é conferida no cálculo. "
                    + "409 em atividade resumo (com subtarefas) ou do cliente.")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}/atividades/{atividadeId}/distribuicao")
    public AtividadeResponse definirDistribuicao(@PathVariable Long id, @PathVariable Long atividadeId,
                                                 @RequestBody @NotNull List<@Valid @NotNull DivisaoRequest> divisoes) {
        return service.definirDistribuicao(id, atividadeId, divisoes);
    }

    @Operation(summary = "Define as dependências da atividade",
            description = "Substitui a lista. Ciclos são apontados no cálculo.")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}/atividades/{atividadeId}/dependencias")
    public AtividadeResponse definirDependencias(@PathVariable Long id, @PathVariable Long atividadeId,
                                                 @RequestBody @Valid DependenciasRequest req) {
        return service.definirDependencias(id, atividadeId, req);
    }

    // ----- deslocamentos e feriados extras -----

    @Operation(summary = "Cria um deslocamento", description = "horas entre 0 e a jornada (8h).")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/deslocamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public DeslocamentoResponse criarDeslocamento(@PathVariable Long id, @RequestBody @Valid DeslocamentoRequest req) {
        return service.criarDeslocamento(id, req);
    }

    @Operation(summary = "Atualiza um deslocamento")
    @PreAuthorize(EDITORES)
    @PutMapping("/planejamentos/{id}/deslocamentos/{deslocamentoId}")
    public DeslocamentoResponse atualizarDeslocamento(@PathVariable Long id, @PathVariable Long deslocamentoId,
                                                      @RequestBody @Valid DeslocamentoRequest req) {
        return service.atualizarDeslocamento(id, deslocamentoId, req);
    }

    @Operation(summary = "Apaga um deslocamento")
    @PreAuthorize(EDITORES)
    @DeleteMapping("/planejamentos/{id}/deslocamentos/{deslocamentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirDeslocamento(@PathVariable Long id, @PathVariable Long deslocamentoId) {
        service.excluirDeslocamento(id, deslocamentoId);
    }

    @Operation(summary = "Adiciona um feriado extra (só deste planejamento)", description = "409 se a data já existe.")
    @PreAuthorize(EDITORES)
    @PostMapping("/planejamentos/{id}/feriados-extras")
    @ResponseStatus(HttpStatus.CREATED)
    public FeriadoExtraResponse criarFeriadoExtra(@PathVariable Long id, @RequestBody @Valid FeriadoExtraRequest req) {
        return service.criarFeriadoExtra(id, req);
    }

    @Operation(summary = "Apaga um feriado extra")
    @PreAuthorize(EDITORES)
    @DeleteMapping("/planejamentos/{id}/feriados-extras/{feriadoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirFeriadoExtra(@PathVariable Long id, @PathVariable Long feriadoId) {
        service.excluirFeriadoExtra(id, feriadoId);
    }

    // ----- carga -----

    @Operation(summary = "Carga dos analistas por dia",
            description = "Soma serviço e viagem do último cálculo dos planejamentos EM_PLANEJAMENTO, ENVIADO e "
                    + "CRIADO_ARTIA, com o detalhe por planejamento. Período de até 366 dias.")
    @PreAuthorize(EDITORES)
    @GetMapping("/carga")
    public List<CargaResponse> carga(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return calculos.carga(de, ate);
    }
}
