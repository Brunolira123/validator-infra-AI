package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administração de usuários: só ADMIN/DIRETORIA, sem tirar o próprio acesso, e sem apagar quem tem registros.
 */
@IntegrationTest
class UsuarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
    }

    @Test
    void criaUsuarioQueConsegueEntrar() throws Exception {
        String login = novoLogin();
        criar(login, "SUPORTE");

        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].login", hasItem(login)));
        token(login, "senha-forte-1");
    }

    @Test
    void loginRepetidoDa409() throws Exception {
        String login = novoLogin();
        criar(login, "COMERCIAL");

        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(login, "outro." + login + "@vr.com.br", "COMERCIAL")))
                .andExpect(status().isConflict());
    }

    @Test
    void senhaCurtaDa400() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","nome":"Teste","email":"%s@vr.com.br","perfil":"COMERCIAL","senha":"123"}
                                """.formatted(novoLogin(), novoLogin())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quemNaoEhAdminNaoAcessa() throws Exception {
        String login = novoLogin();
        criar(login, "COMERCIAL");

        mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token(login, "senha-forte-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioDesativadoNaoEntraETrocaDeSenhaFunciona() throws Exception {
        String login = novoLogin();
        long id = criar(login, "TECNICO");

        mockMvc.perform(put("/api/usuarios/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizacao(login, false, "nova-senha-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"nova-senha-2\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/usuarios/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizacao(login, true, "")))
                .andExpect(status().isOk());
        token(login, "nova-senha-2");
    }

    @Test
    void excluiUsuarioSemRegistros() throws Exception {
        long id = criar(novoLogin(), "COMERCIAL");

        mockMvc.perform(delete("/api/usuarios/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/usuarios/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizacao("x", true, "")))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioComRegistrosNaoEhExcluidoEDa409() throws Exception {
        String login = novoLogin();
        long id = criar(login, "SUPORTE");

        // Um "Consultar agora" pedido por ele fica gravado com o usuário
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Mercado " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long cliente = ((Number) JsonPath.read(resposta, "$.cliente.id")).longValue();
        mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente)
                        .header(HttpHeaders.AUTHORIZATION, token(login, "senha-forte-1")))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/usuarios/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Desative")));
    }

    @Test
    void naoExcluiNemDesativaOProprioUsuario() throws Exception {
        String resposta = mockMvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString();
        long adminId = ((Number) JsonPath.<java.util.List<Object>>read(resposta, "$[?(@.login == 'admin')].id").get(0)).longValue();

        mockMvc.perform(delete("/api/usuarios/{id}", adminId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/usuarios/{id}", adminId)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Administrador","email":"admin@vr.com.br","perfil":"ADMIN","ativo":false}
                                """))
                .andExpect(status().isConflict());
    }

    private long criar(String login, String perfil) throws Exception {
        String resposta = mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCriacao(login, login + "@vr.com.br", perfil)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value(login))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private static String corpoCriacao(String login, String email, String perfil) {
        return """
                {"login":"%s","nome":"Usuário %s","email":"%s","perfil":"%s","senha":"senha-forte-1"}
                """.formatted(login, login, email, perfil);
    }

    private static String corpoAtualizacao(String login, boolean ativo, String senha) {
        return """
                {"nome":"Usuário %s","email":"%s@vr.com.br","perfil":"TECNICO","ativo":%s,"senha":"%s"}
                """.formatted(login, login, ativo, senha);
    }

    private static String novoLogin() {
        return "u." + UUID.randomUUID().toString().substring(0, 8);
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
