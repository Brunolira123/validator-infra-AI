package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de autenticação ponta a ponta com H2 em memória (profile "test"); o admin vem do DataSeeder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Nested
    class Login {

        @Test
        void deveRetornarTokensQuandoCredenciaisForemValidas() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credenciais("admin", "admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.expiraEm").value(900))
                    .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                    .andExpect(jsonPath("$.usuario.nome").value("Administrador"))
                    .andExpect(jsonPath("$.usuario.email").value("admin@vr.com.br"))
                    .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"));
        }

        @Test
        void deveRetornar401QuandoSenhaEstiverErrada() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credenciais("admin", "errada")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas"));
        }

        @Test
        void deveRetornarMesmaMensagemQuandoLoginNaoExistir() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credenciais("nao-existe", "qualquer")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas"));
        }
    }

    @Nested
    class AcessoAosEndpoints {

        @Test
        void deveRetornar401SemToken() throws Exception {
            mockMvc.perform(get("/api/clientes"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.path").value("/api/clientes"))
                    .andExpect(jsonPath("$.mensagem", not(emptyOrNullString())));
        }

        @Test
        void deveRetornar200ComTokenValido() throws Exception {
            mockMvc.perform(get("/api/clientes")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login("admin", "admin").token())))
                    .andExpect(status().isOk());
        }

        @Test
        void deveRetornar401ComTokenAdulterado() throws Exception {
            String token = login("admin", "admin").token();
            mockMvc.perform(get("/api/clientes")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token.substring(0, token.length() - 4) + "abcd")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void naoDeveAceitarRefreshTokenComoAccessToken() throws Exception {
            mockMvc.perform(get("/api/clientes")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login("admin", "admin").refreshToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deveRetornar403QuandoPerfilNaoTiverPermissaoNoMetodo() throws Exception {
            criarUsuarioSeNaoExistir("comercial.teste", Perfil.COMERCIAL);

            mockMvc.perform(put("/api/equipamentos/1/analise/revisar")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login("comercial.teste", "senha123").token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensagem").value("Acesso negado para o seu perfil"));
        }

        @Test
        void deveRetornar403NosEndpointsDeTesteParaNaoAdmin() throws Exception {
            criarUsuarioSeNaoExistir("comercial.teste", Perfil.COMERCIAL);

            mockMvc.perform(post("/api/motor/avaliar")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login("comercial.teste", "senha123").token())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class RefreshELogout {

        @Test
        void deveGerarNovoAccessTokenComRefreshValido() throws Exception {
            String refresh = login("admin", "admin").refreshToken();

            String resposta = mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refresh)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.expiraEm").value(900))
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform(get("/api/clientes")
                            .header(HttpHeaders.AUTHORIZATION, bearer(JsonPath.read(resposta, "$.token"))))
                    .andExpect(status().isOk());
        }

        @Test
        void deveRetornar401QuandoRefreshForAccessToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(login("admin", "admin").token())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deveRevogarRefreshNoLogout() throws Exception {
            String refresh = login("admin", "admin").refreshToken();

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refresh)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refresh)))
                    .andExpect(status().isUnauthorized());
        }
    }

    private Tokens login(String login, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciais(login, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(resposta, "$.token"), JsonPath.read(resposta, "$.refreshToken"));
    }

    private void criarUsuarioSeNaoExistir(String login, Perfil perfil) {
        if (usuarioRepo.findByLogin(login).isPresent()) {
            return;
        }
        Usuario u = new Usuario();
        u.setLogin(login);
        u.setSenha(passwordEncoder.encode("senha123"));
        u.setNome("Usuário " + perfil);
        u.setEmail(login + "@vr.com.br");
        u.setPerfil(perfil);
        u.setAtivo(true);
        usuarioRepo.save(u);
    }

    private static String credenciais(String login, String senha) {
        return "{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}";
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Tokens(String token, String refreshToken) {}
}
