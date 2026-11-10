package br.com.vrinteriorpaulista.validator_infra.projetos.controller;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.*;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.CategoriaService;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.ContatoClienteService;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.FeriadoService;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Cadastros do módulo Projetos. Só PROJETOS, ADMIN e DIRETORIA (pela hierarquia de papéis). */
@RestController
@RequestMapping("/api/projetos")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJETOS')")
@Tag(name = "Projetos: cadastros", description = "Categorias, feriados, templates e contatos do cliente")
public class CadastrosProjetosController {

    private final CategoriaService categorias;
    private final FeriadoService feriados;
    private final TemplateService templates;
    private final ContatoClienteService contatos;

    public CadastrosProjetosController(CategoriaService categorias, FeriadoService feriados, TemplateService templates,
                                       ContatoClienteService contatos) {
        this.categorias = categorias;
        this.feriados = feriados;
        this.templates = templates;
        this.contatos = contatos;
    }

    // ----- categorias -----

    @Operation(summary = "Lista as categorias", description = "ativas=true esconde as inativas.")
    @GetMapping("/categorias")
    public List<CategoriaResponse> listarCategorias(@RequestParam(defaultValue = "false") boolean ativas) {
        return categorias.listar(ativas);
    }

    @Operation(summary = "Cria uma categoria", description = "409 se o nome já existe (sem diferenciar maiúsculas).")
    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse criarCategoria(@RequestBody @Valid CategoriaRequest req) {
        return categorias.criar(req);
    }

    @Operation(summary = "Atualiza uma categoria", description = "Não muda as horas de atividades já criadas.")
    @PutMapping("/categorias/{id}")
    public CategoriaResponse atualizarCategoria(@PathVariable Long id, @RequestBody @Valid CategoriaRequest req) {
        return categorias.atualizar(id, req);
    }

    @Operation(summary = "Inativa uma categoria")
    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarCategoria(@PathVariable Long id) {
        categorias.inativar(id);
    }

    // ----- feriados -----

    @Operation(summary = "Lista os feriados", description = "Filtros opcionais: ano e escopo (NACIONAL, LOCAL).")
    @GetMapping("/feriados")
    public List<FeriadoResponse> listarFeriados(@RequestParam(required = false) Integer ano,
                                                @RequestParam(required = false) EscopoFeriado escopo) {
        return feriados.listar(ano, escopo);
    }

    @Operation(summary = "Cria um feriado",
            description = "LOCAL exige uf; com cidade vale só para clientes da cidade, sem cidade para a UF inteira. "
                    + "NACIONAL não aceita cidade nem uf (400).")
    @PostMapping("/feriados")
    @ResponseStatus(HttpStatus.CREATED)
    public FeriadoResponse criarFeriado(@RequestBody @Valid FeriadoRequest req) {
        return feriados.criar(req);
    }

    @Operation(summary = "Atualiza um feriado")
    @PutMapping("/feriados/{id}")
    public FeriadoResponse atualizarFeriado(@PathVariable Long id, @RequestBody @Valid FeriadoRequest req) {
        return feriados.atualizar(id, req);
    }

    @Operation(summary = "Apaga um feriado")
    @DeleteMapping("/feriados/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirFeriado(@PathVariable Long id) {
        feriados.excluir(id);
    }

    // ----- templates -----

    @Operation(summary = "Lista os templates (sem as atividades)", description = "ativos=true esconde os inativos.")
    @GetMapping("/templates")
    public List<TemplateResumoResponse> listarTemplates(@RequestParam(defaultValue = "false") boolean ativos) {
        return templates.listar(ativos);
    }

    @Operation(summary = "Template com as atividades")
    @GetMapping("/templates/{id}")
    public TemplateResponse detalharTemplate(@PathVariable Long id) {
        return templates.detalhar(id);
    }

    @Operation(summary = "Cria um template",
            description = "Atividades por chave: chavePai e dependeDe apontam para chaves do mesmo template (400 se "
                    + "não existirem, se repetirem ou se a hierarquia tiver ciclo). horas null = horas da categoria "
                    + "ao aplicar. 409 se o nome já existe.")
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse criarTemplate(@RequestBody @Valid TemplateRequest req) {
        return templates.criar(req);
    }

    @Operation(summary = "Salva o template (troca as atividades e sobe a versão)")
    @PutMapping("/templates/{id}")
    public TemplateResponse atualizarTemplate(@PathVariable Long id, @RequestBody @Valid TemplateRequest req) {
        return templates.atualizar(id, req);
    }

    @Operation(summary = "Inativa um template")
    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarTemplate(@PathVariable Long id) {
        templates.inativar(id);
    }

    // ----- contatos do cliente -----

    @Operation(summary = "Contatos de um cliente", description = "ativos=true esconde os inativos.")
    @GetMapping("/clientes/{clienteId}/contatos")
    public List<ContatoClienteResponse> listarContatos(@PathVariable Long clienteId,
                                                       @RequestParam(defaultValue = "false") boolean ativos) {
        return contatos.listar(clienteId, ativos);
    }

    @Operation(summary = "Cria um contato do cliente")
    @PostMapping("/clientes/{clienteId}/contatos")
    @ResponseStatus(HttpStatus.CREATED)
    public ContatoClienteResponse criarContato(@PathVariable Long clienteId, @RequestBody @Valid ContatoClienteRequest req) {
        return contatos.criar(clienteId, req);
    }

    @Operation(summary = "Atualiza um contato do cliente")
    @PutMapping("/contatos/{id}")
    public ContatoClienteResponse atualizarContato(@PathVariable Long id, @RequestBody @Valid ContatoClienteRequest req) {
        return contatos.atualizar(id, req);
    }

    @Operation(summary = "Inativa um contato do cliente")
    @DeleteMapping("/contatos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativarContato(@PathVariable Long id) {
        contatos.inativar(id);
    }
}
