package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.request.ClienteRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.CnpjResponseDTO;
import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.repository.ClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.service.brasilapi.BrasilApiClient;
import br.com.vrinteriorpaulista.validator_infra.util.CnpjUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ClienteService {

    private final ClienteRepository repo;
    private final BrasilApiClient brasilApi;

    public ClienteService(ClienteRepository repo, BrasilApiClient brasilApi) {
        this.repo = repo;
        this.brasilApi = brasilApi;
    }

    @Transactional
    public Cliente criar(ClienteRequest req) {
        String cnpj = CnpjUtils.normalizar(req.cnpj());

        if (repo.existsByCnpj(cnpj)) {
            throw new IllegalStateException("Já existe cliente com este CNPJ");
        }

        Cliente c = new Cliente();
        c.setCnpj(cnpj);
        c.setRazaoSocial(req.razaoSocial());
        c.setNomeFantasia(req.nomeFantasia());
        c.setEndereco(req.endereco());
        c.setCidade(req.cidade());
        c.setUf(req.uf());
        c.setTelefone(req.telefone());
        c.setEmail(req.email());

        return repo.save(c);
    }

    public List<Cliente> listar() {
        return repo.findAll();
    }

    public Cliente buscar(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado"));
    }

    public CnpjResponseDTO consultarCnpj(String cnpj) {
        String normalizado = CnpjUtils.normalizar(cnpj);
        BrasilApiCnpjResponse dados = brasilApi.consultarCnpj(normalizado);

        return new CnpjResponseDTO(
                normalizado,
                dados.razao_social(),
                naoVazio(dados.nome_fantasia()),
                montarEndereco(dados),
                dados.municipio(),
                dados.uf(),
                naoVazio(dados.ddd_telefone_1()),
                naoVazio(dados.email())
        );
    }

    private String montarEndereco(BrasilApiCnpjResponse dados) {
        String logradouroNumero = Stream.of(dados.logradouro(), dados.numero())
                .map(this::naoVazio)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        String endereco = Stream.of(naoVazio(logradouroNumero), naoVazio(dados.bairro()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" - "));

        return naoVazio(endereco);
    }

    private String naoVazio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
