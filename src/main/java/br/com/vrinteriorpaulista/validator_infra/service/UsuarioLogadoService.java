package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import br.com.vrinteriorpaulista.validator_infra.security.UsuarioAutenticado;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioLogadoService {

    private static final String NAO_AUTENTICADO = "Usuário não autenticado";

    private final UsuarioRepository usuarioRepo;

    public UsuarioLogadoService(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    /**
     * Dados do token, sem ir ao banco.
     */
    public UsuarioAutenticado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new AuthenticationCredentialsNotFoundException(NAO_AUTENTICADO);
        }
        return usuario;
    }

    /**
     * Entidade do usuário autenticado, buscada pelo id do token.
     */
    public Usuario usuario() {
        return usuarioRepo.findById(atual().id())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(NAO_AUTENTICADO));
    }
}
