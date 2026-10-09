package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.request.ClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.ClienteResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.CnpjResponseDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @PostMapping
    public ClienteResponse criar(@RequestBody @Valid ClienteRequest req) {
        return ClienteResponse.from(service.criar(req));
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return service.listar().stream().map(ClienteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable Long id) {
        return ClienteResponse.from(service.buscar(id));
    }

    @GetMapping("/consulta-cnpj/{cnpj}")
    public CnpjResponseDTO consultarCnpj(@PathVariable String cnpj) {
        return service.consultarCnpj(cnpj);
    }
}
