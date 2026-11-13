package br.com.vrinteriorpaulista.validator_infra.monitor.dto;

import java.util.Comparator;

/**
 * Ordem numérica de versões (4.4.93 antes de 4.4.100), usada para a versão do banco VR e do agente.
 * Partes numéricas comparam como número, as demais como texto; 2.5.0-rc.1 vem antes de 2.5.0.
 */
public final class OrdemVersao {

    public static final Comparator<String> COMPARADOR = OrdemVersao::comparar;

    private OrdemVersao() {
    }

    public static int comparar(String a, String b) {
        String[] pa = a.trim().split("-", 2);
        String[] pb = b.trim().split("-", 2);
        int r = compararPartes(pa[0], pb[0]);
        if (r != 0) return r;
        if (pa.length == 1 || pb.length == 1) {
            return Integer.compare(pb.length, pa.length);
        }
        return compararPartes(pa[1], pb[1]);
    }

    private static int compararPartes(String a, String b) {
        String[] xa = a.split("\\.");
        String[] xb = b.split("\\.");
        for (int i = 0; i < Math.min(xa.length, xb.length); i++) {
            int r = compararParte(xa[i], xb[i]);
            if (r != 0) return r;
        }
        return Integer.compare(xa.length, xb.length);
    }

    private static int compararParte(String a, String b) {
        boolean na = numerico(a);
        boolean nb = numerico(b);
        if (na && nb) {
            String sa = semZerosAEsquerda(a);
            String sb = semZerosAEsquerda(b);
            int r = Integer.compare(sa.length(), sb.length());
            return r != 0 ? r : sa.compareTo(sb);
        }
        if (na != nb) return na ? -1 : 1;
        return a.compareToIgnoreCase(b);
    }

    private static boolean numerico(String s) {
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /** Compara pelo tamanho e depois pelo texto: aceita números de qualquer tamanho sem estourar o int. */
    private static String semZerosAEsquerda(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }
}
