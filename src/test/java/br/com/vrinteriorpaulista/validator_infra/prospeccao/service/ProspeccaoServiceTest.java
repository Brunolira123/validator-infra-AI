package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.LugarEncontradoResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.dto.PaginaBuscaProspectResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.FotoGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.GooglePlacesClient;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.LugarGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.PaginaGoogle;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao.ACOUGUE;
import static br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao.PADARIA;
import static br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.CategoriaProspeccao.SUPERMERCADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProspeccaoServiceTest {

    private GooglePlacesClient google;
    private ProspeccaoService service;

    @BeforeEach
    void setUp() {
        google = mock(GooglePlacesClient.class);
        CotaDiariaGoogle cota = new CotaDiariaGoogle(100, 1, Clock.systemUTC());
        service = new ProspeccaoService(google, cota, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        service.encerrar();
    }

    @Test
    void consultaTodasAsCategoriasQuandoNaoInforma() {
        when(google.buscarTexto(anyString(), any())).thenReturn(new PaginaGoogle(null, null));

        service.buscar("Limeira", "sp", null, null);

        verify(google, times(CategoriaProspeccao.values().length)).buscarTexto(anyString(), isNull());
        verify(google).buscarTexto("supermercado em Limeira - SP", null);
        verify(google).buscarTexto("distribuidora de bebidas adega em Limeira - SP", null);
    }

    @Test
    void juntaCategoriasSemDuplicarEIgnoraFechados() {
        when(google.buscarTexto(eq("supermercado em Limeira - SP"), isNull()))
                .thenReturn(new PaginaGoogle(List.of(lugar("p1"), lugar("p2")), null));
        when(google.buscarTexto(eq("açougue em Limeira - SP"), isNull()))
                .thenReturn(new PaginaGoogle(List.of(lugar("p1"), fechado("p3")), null));

        PaginaBuscaProspectResponse pagina = service.buscar("Limeira", "SP", List.of(SUPERMERCADO, ACOUGUE), null);

        assertThat(pagina.lugares()).extracting(LugarEncontradoResponse::placeId).containsExactly("p1", "p2");
        assertThat(pagina.lugares().get(0).categorias()).containsExactly(SUPERMERCADO, ACOUGUE);
        assertThat(pagina.lugares().get(1).categorias()).containsExactly(SUPERMERCADO);
        assertThat(pagina.proximaPagina()).isNull();
    }

    @Test
    void carregarMaisSoConsultaCategoriasQueTemPagina() {
        when(google.buscarTexto(eq("supermercado em Limeira - SP"), isNull()))
                .thenReturn(new PaginaGoogle(List.of(lugar("p1")), "tok-super"));
        when(google.buscarTexto(eq("padaria em Limeira - SP"), isNull()))
                .thenReturn(new PaginaGoogle(List.of(lugar("p2")), null));
        when(google.buscarTexto("supermercado em Limeira - SP", "tok-super"))
                .thenReturn(new PaginaGoogle(List.of(lugar("p9")), null));

        String proxima = service.buscar("Limeira", "SP", List.of(SUPERMERCADO, PADARIA), null).proximaPagina();
        PaginaBuscaProspectResponse segunda = service.buscar("Limeira", "SP", List.of(SUPERMERCADO, PADARIA), proxima);

        assertThat(segunda.lugares()).extracting(LugarEncontradoResponse::placeId).containsExactly("p9");
        assertThat(segunda.proximaPagina()).isNull();
        verify(google, never()).buscarTexto(eq("padaria em Limeira - SP"), anyString());
    }

    @Test
    void paginaAdulteradaDa400() {
        assertThatThrownBy(() -> service.buscar("Limeira", "SP", null, "nao-e-um-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.buscar("Limeira", "SP", null, "e30"))   // "{}" em base64url
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesmaBuscaNoDiaNaoChamaOGoogleDeNovo() {
        when(google.buscarTexto(anyString(), any())).thenReturn(new PaginaGoogle(List.of(lugar("p1")), null));

        service.buscar("Limeira", "SP", List.of(PADARIA), null);
        service.buscar(" Limeira ", "sp", List.of(PADARIA), null);

        verify(google, times(1)).buscarTexto(anyString(), any());
    }

    @Test
    void validaCidadeEUf() {
        assertThatThrownBy(() -> service.buscar(" ", "SP", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.buscar("Limeira", "São", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void montaFotoUrlRotaEAtribuicao() {
        when(google.buscarTexto(anyString(), any())).thenReturn(new PaginaGoogle(List.of(lugar("p1")), null));

        LugarEncontradoResponse lugar = service.buscar("Limeira", "SP", List.of(PADARIA), null).lugares().get(0);

        assertThat(lugar.fotoUrl()).isEqualTo("/prospeccao/foto?nome=places%2Fp1%2Fphotos%2Ff1&w=400");
        assertThat(lugar.fotoAutor()).isEqualTo("Ana");
        assertThat(lugar.rotaUrl())
                .isEqualTo("https://www.google.com/maps/dir/?api=1&destination=-22.5%2C-47.4&destination_place_id=p1");
        assertThat(lugar.prospectId()).isNull();
    }

    @Test
    void fotoSoAceitaReferenciaDoGoogle() {
        for (String nome : List.of("https://evil.com/x", "places/p1/photos/../../x", "places/p1/media", "")) {
            assertThatThrownBy(() -> service.foto(nome, 400)).isInstanceOf(IllegalArgumentException.class);
        }
        verify(google, never()).baixarFoto(anyString(), anyInt());
    }

    @Test
    void fotoUsaCacheELimitaLarguraECota() {
        when(google.baixarFoto(anyString(), anyInt())).thenReturn(new FotoGoogle(new byte[]{1}, "image/jpeg"));

        service.foto("places/p1/photos/f1", 5000);
        service.foto("places/p1/photos/f1", 5000);

        verify(google, times(1)).baixarFoto("places/p1/photos/f1", 800);
        // a cota de teste é 1 foto/dia: a próxima foto nova já não vai ao Google
        assertThatThrownBy(() -> service.foto("places/p2/photos/f2", 400)).isInstanceOf(EntityNotFoundException.class);
        verify(google, never()).baixarFoto(eq("places/p2/photos/f2"), anyInt());
    }

    private static LugarGoogle lugar(String id) {
        return new LugarGoogle(id, new LugarGoogle.Texto("Lugar " + id), "Rua " + id,
                new LugarGoogle.Localizacao(-22.5, -47.4),
                List.of(new LugarGoogle.Foto("places/" + id + "/photos/f1", List.of(new LugarGoogle.Autor("Ana", "https://maps/ana")))),
                "OPERATIONAL", "https://maps/" + id);
    }

    private static LugarGoogle fechado(String id) {
        return new LugarGoogle(id, new LugarGoogle.Texto("Fechado"), null, null, null,
                LugarGoogle.FECHADO_DEFINITIVAMENTE, null);
    }
}
