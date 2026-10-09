package br.com.vrinteriorpaulista.validator_infra.security;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final int TAMANHO_MINIMO_SEGREDO = 32;
    private static final String CLAIM_ID_USUARIO = "idUsuario";
    private static final String CLAIM_PERFIL = "perfil";
    private static final String CLAIM_TIPO = "tipo";
    private static final String TIPO_ACCESS = "access";
    private static final String TIPO_REFRESH = "refresh";

    private final SecretKey chave;
    private final Duration expiracaoAccess;
    private final Duration expiracaoRefresh;

    public JwtService(@Value("${app.jwt.secret}") String segredo,
                      @Value("${app.jwt.access-expiration-minutes:15}") long minutosAccess,
                      @Value("${app.jwt.refresh-expiration-days:7}") long diasRefresh) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException("app.jwt.secret precisa ter no mínimo 32 bytes (256 bits)");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.expiracaoAccess = Duration.ofMinutes(minutosAccess);
        this.expiracaoRefresh = Duration.ofDays(diasRefresh);
    }

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getLogin())
                .claim(CLAIM_ID_USUARIO, usuario.getId())
                .claim(CLAIM_PERFIL, usuario.getPerfil().name())
                .claim(CLAIM_TIPO, TIPO_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoAccess)))
                .signWith(chave)
                .compact();
    }

    public String gerarRefreshToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getLogin())
                .claim(CLAIM_ID_USUARIO, usuario.getId())
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoRefresh)))
                .signWith(chave)
                .compact();
    }

    public long getExpiracaoAccessSegundos() {
        return expiracaoAccess.toSeconds();
    }

    /**
     * Vazio se o token for inválido, expirado ou não for um access token (um refresh não autentica requisições).
     */
    public Optional<UsuarioAutenticado> lerAccessToken(String token) {
        return lerClaims(token, TIPO_ACCESS).map(c -> new UsuarioAutenticado(
                idUsuario(c),
                c.getSubject(),
                Perfil.valueOf(c.get(CLAIM_PERFIL, String.class))
        ));
    }

    /**
     * Vazio se o token for inválido, expirado ou não for um refresh token.
     */
    public Optional<RefreshToken> lerRefreshToken(String token) {
        return lerClaims(token, TIPO_REFRESH).map(c -> new RefreshToken(
                idUsuario(c),
                c.getId(),
                c.getExpiration().toInstant()
        ));
    }

    private Optional<Claims> lerClaims(String token, String tipoEsperado) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!tipoEsperado.equals(claims.get(CLAIM_TIPO, String.class))) {
                return Optional.empty();
            }
            idUsuario(claims);
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException | ClassCastException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private Long idUsuario(Claims claims) {
        return ((Number) claims.get(CLAIM_ID_USUARIO)).longValue();
    }

    public record RefreshToken(Long usuarioId, String id, Instant expiraEm) {}
}
