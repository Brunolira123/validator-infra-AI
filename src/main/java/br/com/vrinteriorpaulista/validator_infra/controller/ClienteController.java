package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.ClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.CnpjResponseDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Clientes", description = "Cadastro de clientes e consulta de CNPJ na Receita (BrasilAPI)")
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @Operation(summary = "Cadastra um cliente", description = "Aceita CNPJ numérico ou alfanumérico, com ou sem formatação. Retorna 409 se o CNPJ já existir.")
    @PostMapping
    public ClienteResponse criar(@RequestBody @Valid ClienteRequest req) {
        return ClienteResponse.from(service.criar(req));
    }

    @Operation(summary = "Lista todos os clientes")
    @GetMapping
    public List<ClienteResponse> listar() {
        return service.listar().stream().map(ClienteResponse::from).toList();
    }

    @Operation(summary = "Busca um cliente pelo id")
    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable Long id) {
        return ClienteResponse.from(service.buscar(id));
    }

    @Operation(summary = "Consulta CNPJ na Receita para autocompletar o cadastro", description = "Envie o CNPJ sem a barra (/). Retorna 404 se o CNPJ não existir na Receita e 503 se a BrasilAPI estiver indisponível.")
    @GetMapping("/consulta-cnpj/{cnpj}")
    public CnpjResponseDTO consultarCnpj(@PathVariable String cnpj) {
        return service.consultarCnpj(cnpj);
    }
}
