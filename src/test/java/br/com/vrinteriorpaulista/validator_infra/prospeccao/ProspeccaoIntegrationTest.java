package br.com.vrinteriorpaulista.validator_infra.prospeccao;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.exception.LimiteExcedidoException;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.FotoGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.GooglePlacesClient;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.LugarGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.PaginaGoogle;
import br.com.vrinteriorpaulista.validator_infra.service.brasilapi.BrasilApiClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Busca de prospects pelo controller, com o Google mockado. Cada teste usa uma cidade própria porque o cache
 * de 24h do service é compartilhado pelo contexto Spring.
 */
@IntegrationTest
class ProspeccaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GooglePlacesClient google;

    /** Não usado aqui: mesmo conjunto de mocks do ProspectIntegrationTest, para reaproveitar o contexto Spring. */
    @MockitoBean
    private BrasilApiClient brasilApi;

    private String admin;
    private String cidade;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        cidade = "Cidade " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void buscaDevolveLugaresComFotoRotaECategorias() throws Exception {
        LugarGoogle lugar = new LugarGoogle("p1", new LugarGoogle.Texto("Supermercado Bom Preço"), "Rua A, 10",
                new LugarGoogle.Localizacao(-22.5, -47.4),
                List.of(new LugarGoogle.Foto("places/p1/photos/f1", List.of(new LugarGoogle.Autor("Ana", "https://maps/ana")))),
                "OPERATIONAL", "https://maps/p1");
        when(google.buscarTexto(startsWith("supermercado em " + cidade), isNull()))
                .thenReturn(new PaginaGoogle(List.of(lugar), "tok-2"));

        mockMvc.perform(get("/api/prospeccao/busca")
                        .param("cidade", cidade).param("uf", "sp").param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares[0].placeId").value("p1"))
                .andExpect(jsonPath("$.lugares[0].nome").value("Supermercado Bom Preço"))
                .andExpect(jsonPath("$.lugares[0].categorias[0]").value("SUPERMERCADO"))
                .andExpect(jsonPath("$.lugares[0].fotoUrl").value("/prospeccao/foto?nome=places%2Fp1%2Fphotos%2Ff1&w=400"))
                .andExpect(jsonPath("$.lugares[0].fotoAutor").value("Ana"))
                .andExpect(jsonPath("$.lugares[0].rotaUrl").exists())
                .andExpect(jsonPath("$.proximaPagina").isNotEmpty());
    }

    @Test
    void comercialEPodeBuscarSuporteNao() throws Exception {
        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(null, null));

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .header(HttpHeaders.AUTHORIZATION, usuario("COMERCIAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares").isEmpty());
        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .header(HttpHeaders.AUTHORIZATION, usuario("SUPORTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semChaveConfiguradaDa503ComMensagem() throws Exception {
        when(google.buscarTexto(anyString(), isNull()))
                .thenThrow(new ServicoIndisponivelException("Busca de prospects não configurada", null));

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensagem").value("Busca de prospects não configurada"));
    }

    @Test
    void limiteDiarioDa429() throws Exception {
        when(google.buscarTexto(anyString(), isNull()))
                .thenThrow(new LimiteExcedidoException("Limite diário de buscas atingido, tente amanhã"));

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "PADARIA")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").value("Limite diário de buscas atingido, tente amanhã"));
    }

    @Test
    void categoriaInexistenteDa400() throws Exception {
        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "FARMACIA")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cidadeComMaisDe80CaracteresDa400() throws Exception {
        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", "a".repeat(81)).param("uf", "SP")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fotoVemDoProxyComCachePrivado() throws Exception {
        String nome = "places/" + UUID.randomUUID().toString().replace("-", "") + "/photos/f1";
        when(google.baixarFoto(anyString(), anyInt())).thenReturn(new FotoGoogle(new byte[]{9, 8, 7}, "image/jpeg"));

        mockMvc.perform(get("/api/prospeccao/foto").param("nome", nome).param("w", "400")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{9, 8, 7}))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, private"));
    }

    @Test
    void fotoComNomeForaDoPadraoDa400() throws Exception {
        mockMvc.perform(get("/api/prospeccao/foto").param("nome", "https://evil.com/x.png")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semLoginDa401() throws Exception {
        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP"))
                .andExpect(status().isUnauthorized());
    }

    private String usuario(String perfil) throws Exception {
        String login = "u." + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","nome":"Usuário %s","email":"%s@vr.com.br","perfil":"%s","senha":"senha-forte-1"}
                                """.formatted(login, login, login, perfil)))
                .andExpect(status().isCreated());
        return token(login, "senha-forte-1");
    }

    private String token(String login, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(resposta, "$.token");
    }
}
