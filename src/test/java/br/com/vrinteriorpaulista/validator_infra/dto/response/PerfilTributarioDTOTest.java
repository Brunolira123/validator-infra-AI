package br.com.vrinteriorpaulista.validator_infra.dto.response;

import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse.RegimeTributario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerfilTributarioDTOTest {

    @Test
    void optanteDoSimplesFicaForaDoPerfil() {
        PerfilTributarioDTO perfil = PerfilTributarioDTO.from(dados(true, false, null));

        assertThat(perfil.foraDoPerfil()).isTrue();
        assertThat(perfil.motivo()).isEqualTo("Optante pelo Simples");
    }

    @Test
    void meiFicaForaDoPerfilMesmoSendoSimples() {
        PerfilTributarioDTO perfil = PerfilTributarioDTO.from(dados(true, true, null));

        assertThat(perfil.foraDoPerfil()).isTrue();
        assertThat(perfil.motivo()).isEqualTo("MEI");
    }

    @Test
    void lucroPresumidoNoAnoMaisRecenteFicaForaDoPerfil() {
        PerfilTributarioDTO perfil = PerfilTributarioDTO.from(dados(false, false, List.of(
                new RegimeTributario(2023, "LUCRO REAL"),
                new RegimeTributario(2025, "LUCRO PRESUMIDO"))));

        assertThat(perfil.foraDoPerfil()).isTrue();
        assertThat(perfil.motivo()).isEqualTo("Lucro presumido em 2025");
        assertThat(perfil.regimeTributario()).isEqualTo("LUCRO PRESUMIDO");
        assertThat(perfil.regimeAno()).isEqualTo(2025);
    }

    @Test
    void lucroRealNoAnoMaisRecenteEstaNoPerfil() {
        PerfilTributarioDTO perfil = PerfilTributarioDTO.from(dados(false, false, List.of(
                new RegimeTributario(2024, "LUCRO PRESUMIDO"),
                new RegimeTributario(2025, "LUCRO REAL"))));

        assertThat(perfil.foraDoPerfil()).isFalse();
        assertThat(perfil.motivo()).isNull();
    }

    @Test
    void semRegimeNemSimplesNaoMarcaForaDoPerfil() {
        PerfilTributarioDTO perfil = PerfilTributarioDTO.from(dados(null, null, null));

        assertThat(perfil.foraDoPerfil()).isFalse();
        assertThat(perfil.regimeTributario()).isNull();
        assertThat(perfil.porte()).isEqualTo("DEMAIS");
    }

    private static BrasilApiCnpjResponse dados(Boolean simples, Boolean mei, List<RegimeTributario> regimes) {
        return new BrasilApiCnpjResponse("12345678000195", "Mercado Teste Ltda", null, "Rua A", "1", "Centro",
                "LIMEIRA", "SP", null, null, simples, mei, "DEMAIS", regimes);
    }
}
