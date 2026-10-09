package br.com.vrinteriorpaulista.validator_infra.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CnpjUtils {

    private static final Pattern CARACTERES_ACEITOS = Pattern.compile("[0-9A-Za-z./\\-\\s]+");
    private static final Pattern CNPJ_NORMALIZADO = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final String MENSAGEM_INVALIDO =
            "CNPJ inválido: informe 14 caracteres (letras e números, com ou sem formatação XX.XXX.XXX/XXXX-XX), "
                    + "sendo os 2 últimos dígitos numéricos";

    private CnpjUtils() {}

    /**
     * Aceita o CNPJ formatado ou não e devolve os 14 caracteres em maiúsculas, sem pontuação.
     * <p>
     * Suporta o CNPJ alfanumérico emitido pela Receita a partir de julho/2026 (12 posições [0-9A-Z]
     * + 2 dígitos verificadores numéricos). O formato antigo, só com dígitos, continua válido.
     * Letras minúsculas são convertidas para maiúsculas.
     */
    public static String normalizar(String cnpj) {
        if (cnpj == null || !CARACTERES_ACEITOS.matcher(cnpj.trim()).matches()) {
            throw new IllegalArgumentException(MENSAGEM_INVALIDO);
        }
        String normalizado = cnpj.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (!CNPJ_NORMALIZADO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(MENSAGEM_INVALIDO);
        }
        return normalizado;
    }
}
