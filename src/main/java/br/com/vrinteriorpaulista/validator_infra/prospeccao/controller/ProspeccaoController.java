package br.com.vrinteriorpaulista.validator_infra.prospeccao.controller;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.*;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.StatusProspect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.TemperaturaNegociacao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.FotoGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.service.ProspeccaoService;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.service.ProspectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@Tag(name = "Prospecção", description = "Busca de comércios de alimentos no Google Places e funil de visita, fechamento e LTO")
@RequestMapping("/api/prospeccao")
@PreAuthorize("hasAnyRole('ADMIN', 'COMERCIAL', 'TECNICO')")
public class ProspeccaoController {

    private final ProspeccaoService service;
    private final ProspectService prospects;

    public ProspeccaoController(ProspeccaoService service, ProspectService prospects) {
        this.service = service;
        this.prospects = prospects;
    }

    @Operation(summary = "Busca comércios de alimentos numa cidade",
            description = "Uma consulta ao Google por categoria (padrão: todas), sem duplicados. Para carregar mais, "
                    + "repita cidade/uf e mande proximaPagina em 'pagina'. 503 se a busca não estiver configurada; "
                    + "429 quando o limite diário de buscas acabar.")
    @GetMapping("/busca")
    public PaginaBuscaProspectResponse buscar(@RequestParam @Size(max = 80) String cidade,
                                              @RequestParam String uf,
                                              @RequestParam(required = false) List<CategoriaProspeccao> categorias,
                                              @RequestParam(required = false) String pagina) {
        return prospects.buscar(cidade, uf, categorias, pagina);
    }

    @Operation(summary = "Foto de um lugar (proxy do Google)",
            description = "nome = places/{placeId}/photos/{ref}, como vem na fotoUrl da busca. 404 se a foto não "
                    + "existir ou o limite diário de fotos acabar (o front mostra o placeholder).")
    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(@RequestParam String nome, @RequestParam(required = false) Integer w) {
        FotoGoogle foto = service.foto(nome, w);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(foto.bytes());
    }

    @Operation(summary = "Salva um lugar da busca como prospect",
            description = "Idempotente por placeId: 201 quando cria, 200 com o existente quando já era prospect. "
                    + "409 se o lugar já é prospect de outro comercial.")
    @PostMapping("/prospects")
    public ResponseEntity<ProspectResponse> salvar(@RequestBody @Valid SalvarProspectRequest req) {
        ProspectService.Salvo salvo = prospects.salvar(req);
        return ResponseEntity.status(salvo.criado() ? HttpStatus.CREATED : HttpStatus.OK).body(salvo.prospect());
    }

    @Operation(summary = "Lista os prospects (aba \"Meus prospects\")",
            description = "COMERCIAL vê só os seus; ADMIN e DIRETORIA veem todos. Sem status, os DESCARTADO ficam de "
                    + "fora (status=DESCARTADO lista eles). Paginada (pagina começa em 0, "
                    + "tamanho até 50). Os dados do Google (lugar) só são buscados para a página devolvida e vêm "
                    + "null quando indisponíveis.")
    @GetMapping("/prospects")
    public PaginaProspectsResponse listar(@RequestParam(required = false) String cidade,
                                          @RequestParam(required = false) String uf,
                                          @RequestParam(required = false) StatusProspect status,
                                          @RequestParam(required = false) TemperaturaNegociacao temperatura,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "20") int tamanho) {
        return prospects.listar(cidade, uf, status, temperatura, pagina, tamanho);
    }

    @Operation(summary = "Busca um prospect pelo id")
    @GetMapping("/prospects/{id}")
    public ProspectResponse detalhar(@PathVariable Long id) {
        return prospects.detalhar(id);
    }

    @Operation(summary = "Registra uma visita",
            description = "ATENDEU exige temperatura; NAO_ATENDEU não aceita temperatura. O prospect vai para VISITADO "
                    + "e a última visita define resultado e temperatura. PERDIDO reabre como VISITADO. 409 se o contrato já foi "
                    + "fechado (FECHADO ou LTO).")
    @PostMapping("/prospects/{id}/visita")
    public ProspectResponse registrarVisita(@PathVariable Long id, @RequestBody @Valid VisitaProspectRequest req) {
        return prospects.registrarVisita(id, req);
    }

    @Operation(summary = "Descarta o prospect", description = "409 se o contrato já foi fechado (FECHADO ou LTO).")
    @PostMapping("/prospects/{id}/descartar")
    public ProspectResponse descartar(@PathVariable Long id) {
        return prospects.descartar(id);
    }

    @Operation(summary = "Fecha o contrato: o prospect vira Cliente",
            description = "Só para prospect VISITADO em que o cliente atendeu (senão 409). Reaproveita o Cliente se o "
                    + "CNPJ já existir (inclusive arquivado); se não, cria com os dados da Receita. fechadoEm = "
                    + "dataFechamento ou hoje. A resposta traz o selo de perfil do CNPJ (só aviso).")
    // Mesmos perfis que criam Cliente e Levantamento nas telas atuais (TECNICO não cria)
    @PreAuthorize("hasAnyRole('COMERCIAL', 'ADMIN')")
    @PostMapping("/prospects/{id}/fechar")
    public ProspectResponse fechar(@PathVariable Long id, @RequestBody @Valid FecharProspectRequest req) {
        return prospects.fechar(id, req);
    }

    @Operation(summary = "Marca a negociação como perdida",
            description = "Só para prospect VISITADO em que o cliente atendeu (senão 409). O lugar fica livre para "
                    + "outro comercial, como o descartado, e pode receber nova visita.")
    @PostMapping("/prospects/{id}/perder")
    public ProspectResponse perder(@PathVariable Long id, @RequestBody @Valid PerderProspectRequest req) {
        return prospects.perder(id, req);
    }

    @Operation(summary = "LTO: cria o Levantamento do cliente fechado",
            description = "Só para prospect FECHADO (senão 409). Depois siga para "
                    + "/comercial/levantamentos/{levantamentoId}.")
    @PreAuthorize("hasAnyRole('COMERCIAL', 'ADMIN')")
    @PostMapping("/prospects/{id}/lto")
    public LtoProspectResponse lto(@PathVariable Long id, @RequestBody @Valid LtoProspectRequest req) {
        return prospects.lto(id, req);
    }
}
