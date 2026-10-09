package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/levantamentos?clienteId=...: filtro, ordenação, 404 e restrição de perfil sem clienteId.
 */
@IntegrationTest
class LevantamentoListagemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveListarSoOsLevantamentosDoClienteDoMaisRecenteParaOMaisAntigo() throws Exception {
        String admin = token("admin", "admin");
        long cliente = criarCliente(admin);
        long outroCliente = criarCliente(admin);
        long primeiro = criarLevantamento(admin, cliente);
        long segundo = criarLevantamento(admin, cliente);
        criarLevantamento(admin, outroCliente);

        String resposta = mockMvc.perform(get("/api/levantamentos").param("clienteId", String.valueOf(cliente))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].clienteRazaoSocial").value("Cliente Listagem"))
                .andExpect(jsonPath("$[0].usuarioNome").value("Administrador"))
                .andReturn().getResponse().getContentAsString();

        List<Number> ids = JsonPath.read(resposta, "$[*].id");
        assertThat(ids).extracting(Number::longValue).containsExactly(segundo, primeiro);
    }

    @Test
    void deveRetornarListaVaziaQuandoClienteNaoTiverLevantamentos() throws Exception {
        String admin = token("admin", "admin");
        long cliente = criarCliente(admin);

        mockMvc.perform(get("/api/levantamentos").param("clienteId", String.valueOf(cliente))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deveRetornar404QuandoClienteNaoExistir() throws Exception {
        mockMvc.perform(get("/api/levantamentos").param("clienteId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, token("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Cliente não encontrado"));
    }

    @Test
    void adminDeveListarTodosSemClienteId() throws Exception {
        String admin = token("admin", "admin");
        long a = criarLevantamento(admin, criarCliente(admin));
        long b = criarLevantamento(admin, criarCliente(admin));

        mockMvc.perform(get("/api/levantamentos").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems((int) a, (int) b)));
    }

    @Test
    void comercialETecnicoDevemListarComClienteId() throws Exception {
        long cliente = criarCliente(token("admin", "admin"));

        for (Perfil perfil : List.of(Perfil.COMERCIAL, Perfil.TECNICO)) {
            mockMvc.perform(get("/api/levantamentos").param("clienteId", String.valueOf(cliente))
                            .header(HttpHeaders.AUTHORIZATION, token(usuario(perfil), "senha123")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void naoAdminNaoDeveListarSemClienteId() throws Exception {
        for (Perfil perfil : List.of(Perfil.COMERCIAL, Perfil.TECNICO)) {
            mockMvc.perform(get("/api/levantamentos")
                            .header(HttpHeaders.AUTHORIZATION, token(usuario(perfil), "senha123")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensagem").value("Acesso negado para o seu perfil"));
        }
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/api/levantamentos").param("clienteId", "1"))
                .andExpect(status().isUnauthorized());
    }

    private String token(String login, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(resposta, "$.token");
    }

    /**
     * Cria (uma vez por contexto) um usuário do perfil e devolve o login.
     */
    private String usuario(Perfil perfil) {
        String login = "listagem." + perfil.name().toLowerCase();
        if (usuarioRepo.findByLogin(login).isEmpty()) {
            Usuario u = new Usuario();
            u.setLogin(login);
            u.setSenha(passwordEncoder.encode("senha123"));
            u.setNome("Usuário " + perfil);
            u.setEmail(login + "@vr.com.br");
            u.setPerfil(perfil);
            u.setAtivo(true);
            usuarioRepo.save(u);
        }
        return login;
    }

    private long criarCliente(String token) throws Exception {
        String cnpj = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String resposta = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Cliente Listagem\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private long criarLevantamento(String token, long clienteId) throws Exception {
        String resposta = mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + clienteId + ",\"qtdPdvs\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }
}
