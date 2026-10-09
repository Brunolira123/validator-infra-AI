package br.com.vrinteriorpaulista.validator_infra.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refresh tokens revogados no logout, indexados pelo jti. Fica em memória: reiniciar a aplicação
 * limpa a lista. Cada entrada some sozinha depois que o token expiraria de qualquer forma.
 */
@Component
public class RefreshTokenBlacklist {

    private final Map<String, Instant> revogados = new ConcurrentHashMap<>();

    public void revogar(String tokenId, Instant expiraEm) {
        limparExpirados();
        revogados.put(tokenId, expiraEm);
    }

    public boolean isRevogado(String tokenId) {
        Instant expiraEm = revogados.get(tokenId);
        if (expiraEm == null) {
            return false;
        }
        if (expiraEm.isBefore(Instant.now())) {
            revogados.remove(tokenId);
            return false;
        }
        return true;
    }

    private void limparExpirados() {
        Instant agora = Instant.now();
        revogados.values().removeIf(expiraEm -> expiraEm.isBefore(agora));
    }
}
