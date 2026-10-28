package br.com.vrinteriorpaulista.validator_infra.prospeccao.google;

import br.com.vrinteriorpaulista.validator_infra.exception.LimiteDiarioAtingidoException;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class GooglePlacesClientTest {

    private static final String BASE = "https://places.test/v1";

    private MockRestServiceServer servidor;
    private GooglePlacesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacesClient(builder.build(), "chave-teste");
    }

    @Test
    void buscaMandaChaveFieldMaskProECorpo() {
        servidor.expect(requestTo(BASE + "/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "chave-teste"))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.CAMPOS_BUSCA))
                .andExpect(jsonPath("$.textQuery").value("padaria em Limeira - SP"))
                .andExpect(jsonPath("$.languageCode").value("pt-BR"))
                .andExpect(jsonPath("$.regionCode").value("BR"))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.pageToken").value("tok-1"))
                .andRespond(withSuccess("""
                        {"places":[{"id":"p1","displayName":{"text":"Padaria Pão Bom","languageCode":"pt"},
                          "formattedAddress":"Rua A, 1 - Limeira","location":{"latitude":-22.5,"longitude":-47.4},
                          "photos":[{"name":"places/p1/photos/f1","authorAttributions":[{"displayName":"Ana","uri":"https://maps/ana"}]}],
                          "businessStatus":"OPERATIONAL","googleMapsUri":"https://maps/p1"}],
                         "nextPageToken":"tok-2"}
                        """, MediaType.APPLICATION_JSON));

        PaginaGoogle pagina = client.buscarTexto("padaria em Limeira - SP", "tok-1");

        assertThat(pagina.nextPageToken()).isEqualTo("tok-2");
        assertThat(pagina.lugares()).singleElement().satisfies(l -> {
            assertThat(l.id()).isEqualTo("p1");
            assertThat(l.displayName().text()).isEqualTo("Padaria Pão Bom");
            assertThat(l.location().latitude()).isEqualTo(-22.5);
            assertThat(l.photos().get(0).authorAttributions().get(0).displayName()).isEqualTo("Ana");
        });
        servidor.verify();
    }

    @Test
    void fieldMaskNaoPedeCamposDaFaixaEnterprise() {
        assertThat(GooglePlacesClient.CAMPOS_BUSCA)
                .doesNotContain("rating", "userRatingCount", "PhoneNumber", "websiteUri");
    }

    @Test
    void buscaSemResultadoViraListaVazia() {
        servidor.expect(requestTo(BASE + "/places:searchText"))
                .andExpect(jsonPath("$.pageToken").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        PaginaGoogle pagina = client.buscarTexto("empório em Nada - SP", null);

        assertThat(pagina.lugares()).isEmpty();
        assertThat(pagina.nextPageToken()).isNull();
    }

    @Test
    void erro5xxViraServicoIndisponivel() {
        servidor.expect(requestTo(BASE + "/places:searchText")).andRespond(withServerError());

        assertThatThrownBy(() -> client.buscarTexto("x", null)).isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void chaveRecusadaViraServicoIndisponivel() {
        servidor.expect(requestTo(BASE + "/places:searchText")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.buscarTexto("x", null)).isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void cotaDoGoogleEsgotadaViraLimiteDiario() {
        servidor.expect(requestTo(BASE + "/places:searchText")).andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.buscarTexto("x", null)).isInstanceOf(LimiteDiarioAtingidoException.class);
    }

    @Test
    void semChaveNemChamaOGoogle() {
        GooglePlacesClient semChave = new GooglePlacesClient(RestClient.builder().baseUrl(BASE).build(), " ");

        assertThatThrownBy(() -> semChave.buscarTexto("x", null))
                .isInstanceOf(ServicoIndisponivelException.class)
                .hasMessage(GooglePlacesClient.MENSAGEM_NAO_CONFIGURADA);
        assertThatThrownBy(() -> semChave.baixarFoto("places/p/photos/f", 400))
                .isInstanceOf(ServicoIndisponivelException.class);
    }

    @Test
    void detalhesUsaFieldMaskSemPrefixo() {
        servidor.expect(requestTo(BASE + "/places/p1?languageCode=pt-BR&regionCode=BR"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.CAMPOS_LUGAR))
                .andRespond(withSuccess("{\"id\":\"p1\",\"displayName\":{\"text\":\"Mercado\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.detalhes("p1")).hasValueSatisfying(l -> assertThat(l.displayName().text()).isEqualTo("Mercado"));
    }

    @Test
    void detalhesDeLugarQueSumiuVoltaVazio() {
        servidor.expect(requestTo(BASE + "/places/sumiu?languageCode=pt-BR&regionCode=BR"))
                .andRespond(withResourceNotFound());

        assertThat(client.detalhes("sumiu")).isEmpty();
    }

    @Test
    void baixaFotoComTipo() {
        servidor.expect(requestTo(BASE + "/places/p1/photos/f1/media?maxWidthPx=400&skipHttpRedirect=false"))
                .andExpect(header("X-Goog-Api-Key", "chave-teste"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));

        FotoGoogle foto = client.baixarFoto("places/p1/photos/f1", 400);

        assertThat(foto.bytes()).containsExactly(1, 2, 3);
        assertThat(foto.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
    }
}
