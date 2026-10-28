package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse.RegimeTributario;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Selo de perfil a partir dos dados da Receita: o público-alvo é normalmente Lucro Real.
 * Simples, MEI ou regime mais recente diferente de Lucro Real = fora do perfil. Só avisa, nunca bloqueia.
 *
 * @param regimeTributario forma de tributação do ano mais recente informado (null quando a Receita não traz)
 */
public record PerfilTributarioDTO(
        Boolean simples,
        Boolean mei,
        String porte,
        String regimeTributario,
        Integer regimeAno,
        boolean foraDoPerfil,
        String motivo
) {
    public static PerfilTributarioDTO from(BrasilApiCnpjResponse dados) {
        RegimeTributario regime = dados.regime_tributario() == null ? null : dados.regime_tributario().stream()
                .filter(Objects::nonNull)
                .filter(r -> r.ano() != null && r.forma_de_tributacao() != null && !r.forma_de_tributacao().isBlank())
                .max(Comparator.comparing(RegimeTributario::ano))
                .orElse(null);

        String motivo = null;
        if (Boolean.TRUE.equals(dados.opcao_pelo_mei())) {
            motivo = "MEI";
        } else if (Boolean.TRUE.equals(dados.opcao_pelo_simples())) {
            motivo = "Optante pelo Simples";
        } else if (regime != null && !regime.forma_de_tributacao().toUpperCase(Locale.ROOT).contains("LUCRO REAL")) {
            motivo = capitalizar(regime.forma_de_tributacao()) + " em " + regime.ano();
        }

        return new PerfilTributarioDTO(
                dados.opcao_pelo_simples(),
                dados.opcao_pelo_mei(),
                dados.porte(),
                regime != null ? regime.forma_de_tributacao() : null,
                regime != null ? regime.ano() : null,
                motivo != null,
                motivo
        );
    }

    private static String capitalizar(String texto) {
        String minusculo = texto.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(minusculo.charAt(0)) + minusculo.substring(1);
    }
}
