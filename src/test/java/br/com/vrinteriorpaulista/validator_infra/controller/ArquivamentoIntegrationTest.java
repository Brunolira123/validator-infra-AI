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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Soft delete de clientes, levantamentos e fotos (só ADMIN), contra Postgres no Testcontainers.
 */
@IntegrationTest
class ArquivamentoIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String admin;
    private String cnpj;
    private long cliente;
    private long levantamento;
    private long equipamento;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        cnpj = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        cliente = id(criarCliente("Cliente Arquivamento"), "$.id");
        levantamento = criarLevantamento(cliente);
        equipamento = primeiroEquipamento(levantamento);
    }

    // ---- Fotos ----

    @Test
    void adminArquivaFotoQueSomeDaListagemDoDownloadEDaContagem() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/equipamentos/{id}/fotos", equipamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/equipamentos/{id}/fotos/{fotoId}/conteudo", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[0].qtdFotos").value(0));
    }

    @Test
    void fotoNovaNaoRepeteASequenciaDeUmaArquivada() throws Exception {
        enviarFoto(equipamento);
        long segunda = enviarFoto(equipamento);
        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, segunda)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        enviarFoto(equipamento);

        mockMvc.perform(get("/api/equipamentos/{id}/fotos", equipamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sequencia").value(1))
                .andExpect(jsonPath("$[1].sequencia").value(3));
    }

    @Test
    void naoArquivaFotoDeLevantamentoCancelado() throws Exception {
        long foto = enviarFoto(equipamento);
        mockMvc.perform(post("/api/levantamentos/{id}/cancelar", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404AoArquivarFotoDeOutroEquipamentoOuJaArquivada() throws Exception {
        long foto = enviarFoto(equipamento);

        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, 999_999)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, foto)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    // ---- Levantamentos ----

    @Test
    void adminArquivaLevantamentoQueSomeDaBuscaEDaListagemDoCliente() throws Exception {
        mockMvc.perform(delete("/api/levantamentos/{id}", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/levantamentos/{id}", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/levantamentos").param("clienteId", String.valueOf(cliente))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Clientes ----

    @Test
    void adminArquivaClienteJuntoComOsLevantamentos() throws Exception {
        mockMvc.perform(delete("/api/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) cliente))));
        mockMvc.perform(get("/api/levantamentos/{id}", levantamento).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/levantamentos").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) levantamento))));
        mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + cliente + ",\"qtdServidores\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recadastrarCnpjDeClienteArquivadoReativaOMesmoRegistroSemOsLevantamentos() throws Exception {
        mockMvc.perform(delete("/api/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        String recadastro = criarCliente("Cliente Recadastrado");

        mockMvc.perform(get("/api/clientes/{id}", id(recadastro, "$.id")).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente))
                .andExpect(jsonPath("$.razaoSocial").value("Cliente Recadastrado"));
        mockMvc.perform(get("/api/levantamentos").param("clienteId", String.valueOf(cliente))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cnpjDeClienteAtivoContinuaDando409() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Duplicado\"}"))
                .andExpect(status().isConflict());
    }

    // ---- Permissões ----

    @Test
    void comercialETecnicoNaoArquivamNada() throws Exception {
        long foto = enviarFoto(equipamento);

        for (Perfil perfil : List.of(Perfil.COMERCIAL, Perfil.TECNICO)) {
            String token = token(usuario(perfil), "senha123");
            mockMvc.perform(delete("/api/equipamentos/{id}/fotos/{fotoId}", equipamento, foto)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/levantamentos/{id}", levantamento).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    // ---- Helpers ----

    private String criarCliente(String razaoSocial) throws Exception {
        return mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"" + razaoSocial + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long criarLevantamento(long clienteId) throws Exception {
        String resposta = mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + clienteId + ",\"qtdServidores\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = id(resposta, "$.id");
        mockMvc.perform(post("/api/levantamentos/{id}/gerar-equipamentos", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        return id;
    }

    private long primeiroEquipamento(long levantamentoId) throws Exception {
        String resposta = mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return id(resposta, "$[0].id");
    }

    private long enviarFoto(long equipamentoId) throws Exception {
        String resposta = mockMvc.perform(multipart("/api/equipamentos/{id}/fotos", equipamentoId)
                        .file(new MockMultipartFile("foto", "etiqueta.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return id(resposta, "$.id");
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
        String login = "arquivamento." + perfil.name().toLowerCase();
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
