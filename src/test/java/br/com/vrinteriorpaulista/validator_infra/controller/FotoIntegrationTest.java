package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listagem de metadados e download do conteúdo das fotos, contra Postgres no Testcontainers.
 */
@IntegrationTest
class FotoIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String admin;
    private long equipamento;
    private long outroEquipamento;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        List<Long> equipamentos = criarLevantamentoComDoisEquipamentos();
        equipamento = equipamentos.get(0);
        outroEquipamento = equipamentos.get(1);
    }

    @Test
    void deveListarAFotoEnviada() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos", equipamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(foto))
                .andExpect(jsonPath("$[0].nomeOriginal").value("rack servidor.png"))
                .andExpect(jsonPath("$[0].contentType").value("image/png"))
                .andExpect(jsonPath("$[0].tamanhoBytes").value(PNG.length))
                .andExpect(jsonPath("$[0].sequencia").value(1))
                .andExpect(jsonPath("$[0].criadoEm").exists());
    }

    @Test
    void deveRetornarListaVaziaQuandoEquipamentoNaoTiverFotos() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/fotos", outroEquipamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deveRetornar404AoListarFotosDeEquipamentoInexistente() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/fotos", 999_999).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Equipamento não encontrado"));
    }

    @Test
    void deveBaixarOConteudoComContentTypeCorreto() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void tecnicoTambemDeveBaixarOConteudo() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, token(usuario(Perfil.TECNICO), "senha123")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", equipamento, foto))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/equipamentos/{id}/fotos", equipamento))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar404QuandoFotoForDeOutroEquipamento() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", outroEquipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Foto não encontrada"));
    }

    @Test
    void deveRetornar404QuandoFotoNaoExistir() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", equipamento, 999_999)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    private long enviarFoto(long equipamentoId) throws Exception {
        String resposta = mockMvc.perform(multipart("/api/equipamentos/{id}/fotos", equipamentoId)
                        .file(new MockMultipartFile("foto", "rack servidor.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return id(resposta, "$.id");
    }

    private List<Long> criarLevantamentoComDoisEquipamentos() throws Exception {
        String cnpj = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String cliente = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Cliente Fotos\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String levantamento = mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + id(cliente, "$.id") + ",\"qtdServidores\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long levantamentoId = id(levantamento, "$.id");

        mockMvc.perform(post("/api/levantamentos/{id}/gerar-equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        String equipamentos = mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(equipamentos, "$[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private String token(String login, String senha) throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(resposta, "$.token");
    }

    private String usuario(Perfil perfil) {
        String login = "fotos." + perfil.name().toLowerCase();
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

    private static long id(String json, String caminho) {
        return ((Number) JsonPath.read(json, caminho)).longValue();
    }
}
