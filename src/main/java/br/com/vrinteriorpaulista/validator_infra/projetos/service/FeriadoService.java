package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.FeriadoRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.FeriadoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Feriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.FeriadoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FeriadoService {

    private final FeriadoRepository repo;

    public FeriadoService(FeriadoRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<FeriadoResponse> listar(Integer ano, EscopoFeriado escopo) {
        return repo.findAllByOrderByDataAsc().stream()
                .filter(f -> ano == null || f.getData().getYear() == ano)
                .filter(f -> escopo == null || f.getEscopo() == escopo)
                .map(FeriadoResponse::from)
                .toList();
    }

    @Transactional
    public FeriadoResponse criar(FeriadoRequest req) {
        Feriado f = new Feriado();
        preencher(f, req);
        return FeriadoResponse.from(repo.save(f));
    }

    @Transactional
    public FeriadoResponse atualizar(Long id, FeriadoRequest req) {
        Feriado f = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Feriado não encontrado"));
        preencher(f, req);
        return FeriadoResponse.from(f);
    }

    @Transactional
    public void excluir(Long id) {
        Feriado f = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Feriado não encontrado"));
        repo.delete(f);
    }

    /** Nacionais e locais da UF/cidade do cliente. A cidade é comparada sem acento e sem maiúsculas. */
    @Transactional(readOnly = true)
    public Set<LocalDate> datasPara(Cliente cliente) {
        return repo.findAll().stream()
                .filter(f -> f.getEscopo() == EscopoFeriado.NACIONAL || valeParaOCliente(f, cliente))
                .map(Feriado::getData)
                .collect(Collectors.toSet());
    }

    private static boolean valeParaOCliente(Feriado f, Cliente cliente) {
        if (f.getUf() == null || cliente.getUf() == null || !f.getUf().equalsIgnoreCase(cliente.getUf().trim())) {
            return false;
        }
        return f.getCidade() == null || Objects.equals(normalizar(f.getCidade()), normalizar(cliente.getCidade()));
    }

    private void preencher(Feriado f, FeriadoRequest req) {
        String cidade = vazioParaNull(req.cidade());
        String uf = vazioParaNull(req.uf());
        if (req.escopo() == EscopoFeriado.NACIONAL && (cidade != null || uf != null)) {
            throw new IllegalArgumentException("Feriado nacional não tem cidade nem UF");
        }
        if (req.escopo() == EscopoFeriado.LOCAL && uf == null) {
            throw new IllegalArgumentException("Feriado local precisa da UF (e da cidade, se for municipal)");
        }
        f.setData(req.data());
        f.setDescricao(req.descricao().trim());
        f.setEscopo(req.escopo());
        f.setCidade(cidade);
        f.setUf(uf != null ? uf.toUpperCase(Locale.ROOT) : null);
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String normalizar(String s) {
        if (s == null) return null;
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
