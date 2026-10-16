package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.UsuarioAtualizacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.request.UsuarioCriacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.UsuarioAdminResponse;
import br.com.vrinteriorpaulista.validator_infra.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administração de usuários do portal. DIRETORIA entra como ADMIN pela hierarquia de papéis.
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuários", description = "Cadastro, edição e exclusão de usuários do portal")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todos os usuários, ativos e inativos")
    @GetMapping
    public List<UsuarioAdminResponse> listar() {
        return service.listar();
    }

    @Operation(summary = "Cria um usuário", description = "Retorna 409 se o login ou o e-mail já existirem.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioAdminResponse criar(@RequestBody @Valid UsuarioCriacaoRequest req) {
        return service.criar(req);
    }

    @Operation(summary = "Atualiza nome, e-mail, perfil, ativo e, opcionalmente, a senha", description = "Senha vazia mantém a atual. Não dá para desativar nem trocar o perfil do próprio usuário (409).")
    @PutMapping("/{id}")
    public UsuarioAdminResponse atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioAtualizacaoRequest req) {
        return service.atualizar(id, req);
    }

    @Operation(summary = "Exclui um usuário (definitivo)", description = "Retorna 409 para o próprio usuário ou para quem tem registros no sistema: nesse caso, desative.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
