package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.UsuarioAtualizacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.request.UsuarioCriacaoRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.UsuarioAdminResponse;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class UsuarioService {

    private static final int SENHA_MINIMA = 8;

    private final UsuarioRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogadoService usuarioLogado;

    public UsuarioService(UsuarioRepository repo, PasswordEncoder passwordEncoder, UsuarioLogadoService usuarioLogado) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.usuarioLogado = usuarioLogado;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> listar() {
        return repo.findAll(Sort.by("nome")).stream().map(UsuarioAdminResponse::from).toList();
    }

    @Transactional
    public UsuarioAdminResponse criar(UsuarioCriacaoRequest req) {
        String login = req.login().trim();
        String email = req.email().trim();
        if (repo.existsByLogin(login)) {
            throw new IllegalStateException("Já existe um usuário com o login " + login);
        }
        if (repo.existsByEmail(email)) {
            throw new IllegalStateException("Já existe um usuário com o e-mail " + email);
        }

        Usuario u = new Usuario();
        u.setLogin(login);
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setSenha(passwordEncoder.encode(req.senha()));
        u.setAtivo(true);
        repo.save(u);

        log.info("Usuário {} ({}) criado por {}", u.getLogin(), u.getPerfil(), usuarioLogado.atual().login());
        return UsuarioAdminResponse.from(u);
    }

    @Transactional
    public UsuarioAdminResponse atualizar(Long id, UsuarioAtualizacaoRequest req) {
        Usuario u = buscar(id);
        String email = req.email().trim();
        repo.findByEmail(email)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new IllegalStateException("Já existe um usuário com o e-mail " + email);
                });

        // Ninguém tira o próprio acesso sem querer: perfil e ativo do próprio usuário não mudam por aqui
        if (ehOProprio(id) && (!req.ativo() || req.perfil() != u.getPerfil())) {
            throw new IllegalStateException("Você não pode desativar nem trocar o perfil do próprio usuário");
        }

        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setAtivo(req.ativo());
        if (req.senha() != null && !req.senha().isBlank()) {
            if (req.senha().length() < SENHA_MINIMA) {
                throw new IllegalArgumentException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres");
            }
            u.setSenha(passwordEncoder.encode(req.senha()));
        }

        log.info("Usuário {} atualizado por {}", u.getLogin(), usuarioLogado.atual().login());
        return UsuarioAdminResponse.from(u);
    }

    /**
     * Exclusão definitiva. Sem @Transactional de propósito: a violação de chave estrangeira (usuário com
     * levantamentos, fotos, análises...) só aparece no commit do delete, e aqui ela vira uma mensagem.
     */
    public void excluir(Long id) {
        if (ehOProprio(id)) {
            throw new IllegalStateException("Você não pode excluir o próprio usuário");
        }
        Usuario u = buscar(id);
        try {
            repo.delete(u);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "O usuário " + u.getLogin() + " tem registros no sistema (levantamentos, fotos ou análises). Desative-o em vez de excluir.");
        }
        log.info("Usuário {} excluído por {}", u.getLogin(), usuarioLogado.atual().login());
    }

    private boolean ehOProprio(Long id) {
        return id.equals(usuarioLogado.atual().id());
    }

    private Usuario buscar(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }
}
