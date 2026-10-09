package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.request.LoginRequest;
import br.com.vrinteriorpaulista.validator_infra.dto.response.LoginResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.TokenResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.response.UsuarioResponse;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import br.com.vrinteriorpaulista.validator_infra.security.JwtService;
import br.com.vrinteriorpaulista.validator_infra.security.RefreshTokenBlacklist;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String TIPO_TOKEN = "Bearer";
    private static final String CREDENCIAIS_INVALIDAS = "Credenciais inválidas";
    private static final String REFRESH_INVALIDO = "Refresh token inválido ou expirado";

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepo;
    private final JwtService jwtService;
    private final RefreshTokenBlacklist blacklist;

    public AuthService(AuthenticationManager authenticationManager,
                       UsuarioRepository usuarioRepo,
                       JwtService jwtService,
                       RefreshTokenBlacklist blacklist) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepo = usuarioRepo;
        this.jwtService = jwtService;
        this.blacklist = blacklist;
    }

    /**
     * Login inexistente, senha errada e usuário inativo devolvem a mesma mensagem, para não revelar quais logins existem.
     */
    public LoginResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.login(), req.senha()));
        } catch (AuthenticationException e) {
            throw new BadCredentialsException(CREDENCIAIS_INVALIDAS);
        }

        Usuario usuario = usuarioRepo.findByLogin(req.login())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIAIS_INVALIDAS));

        return new LoginResponse(
                jwtService.gerarAccessToken(usuario),
                TIPO_TOKEN,
                jwtService.getExpiracaoAccessSegundos(),
                jwtService.gerarRefreshToken(usuario),
                UsuarioResponse.from(usuario)
        );
    }

    /**
     * Emite um novo access token. O refresh token não é rotacionado nesta versão.
     */
    public TokenResponse refresh(String refreshToken) {
        JwtService.RefreshToken refresh = jwtService.lerRefreshToken(refreshToken)
                .filter(r -> !blacklist.isRevogado(r.id()))
                .orElseThrow(() -> new BadCredentialsException(REFRESH_INVALIDO));

        Usuario usuario = usuarioRepo.findById(refresh.usuarioId())
                .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .orElseThrow(() -> new BadCredentialsException(REFRESH_INVALIDO));

        return new TokenResponse(
                jwtService.gerarAccessToken(usuario),
                TIPO_TOKEN,
                jwtService.getExpiracaoAccessSegundos()
        );
    }

    /**
     * Revoga o refresh token. Token inválido é ignorado (logout é idempotente).
     * O access token já emitido continua válido até expirar.
     */
    public void logout(String refreshToken) {
        jwtService.lerRefreshToken(refreshToken)
                .ifPresent(r -> blacklist.revogar(r.id(), r.expiraEm()));
    }
}
