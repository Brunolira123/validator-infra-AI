package br.com.vrinteriorpaulista.validator_infra.monitor.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token do agente: 256 bits aleatórios. O banco guarda só o SHA-256; como o token já tem entropia alta,
 * não precisa de BCrypt (e o hash direto permite buscar o cliente pelo token).
 */
public final class TokenAgente {

    private static final String PREFIXO = "vrip_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenAgente() {}

    public static String gerar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
