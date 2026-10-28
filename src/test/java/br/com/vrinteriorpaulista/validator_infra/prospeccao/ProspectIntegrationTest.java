package br.com.vrinteriorpaulista.validator_infra.prospeccao;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.exception.ServicoIndisponivelException;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.GooglePlacesClient;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.LugarGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.PaginaGoogle;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.OrigemProspect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.entity.Prospect;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.repository.ProspectRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import br.com.vrinteriorpaulista.validator_infra.service.brasilapi.BrasilApiClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Funil do prospect: salvar, visita, descarte, LTO e visibilidade por usuário. Google e BrasilAPI mockados.
 * Cada teste usa placeId, cidade e CNPJ próprios porque o banco e os caches são compartilhados pelo contexto.
 */
@IntegrationTest
class ProspectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProspectRepository prospectRepo;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @MockitoBean
    private GooglePlacesClient google;

    @MockitoBean
    private BrasilApiClient brasilApi;

    private String admin;
    private String comercial;
    private String cidade;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        comercial = usuario("COMERCIAL");
        cidade = "Cidade " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void salvarEhIdempotentePorPlaceId() throws Exception {
        String placeId = novoPlaceId();

        long id = idDe(salvar(comercial, placeId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NOVO"))
                .andExpect(jsonPath("$.origem").value("GOOGLE"))
                .andExpect(jsonPath("$.uf").value("SP")));

        salvar(comercial, placeId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void comercialNaoEnxergaProspectDeOutro() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId).andExpect(status().isCreated()));
        String outro = usuario("COMERCIAL");

        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade).header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prospects[*].id", not(hasItem((int) id))));
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isNotFound());
        salvar(outro, placeId).andExpect(status().isConflict());

        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.prospects[*].id", hasItem((int) id)));
    }

    @Test
    void visitaAtendeuSemTemperaturaDa400() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        visita(id, "{\"resultado\":\"ATENDEU\"}").andExpect(status().isBadRequest());
    }

    @Test
    void visitaNaoAtendeuComTemperaturaDa400() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        visita(id, "{\"resultado\":\"NAO_ATENDEU\",\"temperatura\":\"FRIA\"}").andExpect(status().isBadRequest());
    }

    @Test
    void ultimaVisitaDefineOProspect() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        visita(id, "{\"resultado\":\"NAO_ATENDEU\",\"latitude\":-22.5,\"longitude\":-47.4}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISITADO"))
                .andExpect(jsonPath("$.resultadoVisita").value("NAO_ATENDEU"));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\",\"observacao\":\"Falar com o dono\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoVisita").value("ATENDEU"))
                .andExpect(jsonPath("$.temperatura").value("QUENTE"))
                .andExpect(jsonPath("$.visitadoEm").isNotEmpty());

        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade)
                        .param("status", "VISITADO").param("temperatura", "QUENTE")
                        .header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.prospects[*].id", hasItem((int) id)));
    }

    @Test
    void ltoAntesDaVisitaDa409() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        lto(comercial, id, novoCnpj()).andExpect(status().isConflict());
    }

    @Test
    void ltoDepoisDeNaoAtendeuDa409() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}");

        lto(comercial, id, novoCnpj()).andExpect(status().isConflict());
    }

    @Test
    void ltoCriaClienteComDadosDaReceitaELevantamento() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"MORNA\"}");
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));

        String resposta = lto(comercial, id, cnpj).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long clienteId = ((Number) JsonPath.read(resposta, "$.clienteId")).longValue();
        long levantamentoId = ((Number) JsonPath.read(resposta, "$.levantamentoId")).longValue();

        mockMvc.perform(get("/api/clientes/{id}", clienteId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cnpj").value(cnpj))
                .andExpect(jsonPath("$.razaoSocial").value("Mercado Receita Ltda"));
        mockMvc.perform(get("/api/levantamentos/{id}", levantamentoId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.status").value("LTO"))
                .andExpect(jsonPath("$.clienteId").value(clienteId));

        // Depois do LTO não volta atrás
        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}").andExpect(status().isConflict());
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isConflict());
    }

    @Test
    void ltoComCnpjJaCadastradoReaproveitaOCliente() throws Exception {
        String cnpj = novoCnpj();
        String resposta = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Já Cadastrado Ltda\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long clienteExistente = ((Number) JsonPath.read(resposta, "$.id")).longValue();

        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}");

        lto(comercial, id, cnpj).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteExistente));
        verify(brasilApi, never()).consultarCnpj(anyString());
    }

    @Test
    void tecnicoNaoFazLto() throws Exception {
        String tecnico = usuario("TECNICO");
        long id = idDe(salvar(tecnico, novoPlaceId()).andExpect(status().isCreated()));
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/visita", id)
                        .header(HttpHeaders.AUTHORIZATION, tecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}"))
                .andExpect(status().isOk());

        lto(tecnico, id, novoCnpj()).andExpect(status().isForbidden());
    }

    @Test
    void descartar() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCARTADO"));
    }

    @Test
    void listaTrazLugarDoGoogleOuNullSemQuebrar() throws Exception {
        String comLugar = novoPlaceId();
        String foraDoAr = novoPlaceId();
        long idComLugar = idDe(salvar(comercial, comLugar));
        long idForaDoAr = idDe(salvar(comercial, foraDoAr));
        when(google.detalhes(comLugar)).thenReturn(Optional.of(lugar(comLugar, "Mercado do Google")));
        when(google.detalhes(foraDoAr)).thenThrow(new ServicoIndisponivelException("Busca do Google indisponível", null));

        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.prospects[?(@.id == " + idComLugar + ")].lugar.nome").value("Mercado do Google"))
                .andExpect(jsonPath("$.prospects[?(@.id == " + idComLugar + ")].lugar.statusProspect").value("NOVO"))
                .andExpect(jsonPath("$.prospects[?(@.id == " + idForaDoAr + ")].lugar").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void listaEhPaginadaESoBuscaDetalhesDaPagina() throws Exception {
        for (int i = 0; i < 3; i++) {
            salvar(comercial, novoPlaceId());
        }

        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade).param("tamanho", "2")
                        .header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.prospects.length()").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        verify(google, org.mockito.Mockito.times(2)).detalhes(anyString());
    }

    @Test
    void buscaMarcaLugarQueJaEhProspect() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(List.of(lugar(placeId, "Mercado")), null));

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares[0].prospectId").value(id))
                .andExpect(jsonPath("$.lugares[0].statusProspect").value("NOVO"));
    }

    @Test
    void buscaMostraProspectDeOutroComercialSoComoDeOutroComercial() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}");
        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(List.of(lugar(placeId, "Mercado")), null));

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, usuario("COMERCIAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares[0].deOutroComercial").value(true))
                .andExpect(jsonPath("$.lugares[0].prospectId").doesNotExist())
                .andExpect(jsonPath("$.lugares[0].statusProspect").doesNotExist())
                .andExpect(jsonPath("$.lugares[0].temperatura").doesNotExist());

        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.lugares[0].deOutroComercial").value(false))
                .andExpect(jsonPath("$.lugares[0].prospectId").value(id))
                .andExpect(jsonPath("$.lugares[0].temperatura").value("QUENTE"));
    }

    @Test
    void ltoComCnpjDeClienteArquivadoReativaOMesmoCliente() throws Exception {
        String cnpj = novoCnpj();
        String resposta = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Antigo Ltda\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long arquivado = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        mockMvc.perform(delete("/api/clientes/{id}", arquivado).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));

        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"MORNA\"}");

        lto(comercial, id, cnpj).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(arquivado));
        mockMvc.perform(get("/api/clientes/{id}", arquivado).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razaoSocial").value("Mercado Receita Ltda"));
    }

    @Test
    void seloDePerfilNaConsultaDeCnpj() throws Exception {
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, false));

        mockMvc.perform(get("/api/clientes/consulta-cnpj/{cnpj}", cnpj).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.foraDoPerfil").value(true))
                .andExpect(jsonPath("$.perfil.motivo").value("Lucro presumido em 2025"))
                .andExpect(jsonPath("$.perfil.porte").value("DEMAIS"));
    }

    @Test
    void bancoTemUniquesEChecksDosEnums() {
        List<String> restricoes = jdbc.queryForList("""
                select pg_get_constraintdef(c.oid)
                from pg_constraint c join pg_class t on t.oid = c.conrelid
                where t.relname in ('prospect', 'prospect_visita') and c.contype in ('u', 'c')
                """, String.class);

        assertThat(restricoes).contains("UNIQUE (place_id)", "UNIQUE (origem, id_externo)");
        assertThat(restricoes).anyMatch(r -> r.contains("'NOVO'") && r.contains("'VISITADO'") && r.contains("'LTO'") && r.contains("'DESCARTADO'"));
        assertThat(restricoes).anyMatch(r -> r.contains("'GOOGLE'") && r.contains("'MANUAL'") && r.contains("'CRM'"));
        assertThat(restricoes).anyMatch(r -> r.contains("'ATENDEU'") && r.contains("'NAO_ATENDEU'"));
        assertThat(restricoes).anyMatch(r -> r.contains("'FRIA'") && r.contains("'MORNA'") && r.contains("'QUENTE'"));
        assertThat(restricoes).anyMatch(r -> r.contains("'SUPERMERCADO'") && r.contains("'BEBIDAS'"));
    }

    /** O ProspectService reconhece a corrida pelo nome da constraint na mensagem: garante que o Postgres o devolve. */
    @Test
    void violacaoDoPlaceIdTrazONomeDaConstraint() {
        String placeId = novoPlaceId();
        prospectRepo.saveAndFlush(prospectDireto(placeId));

        assertThatThrownBy(() -> prospectRepo.saveAndFlush(prospectDireto(placeId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(Prospect.UK_PLACE_ID);
    }

    private Prospect prospectDireto(String placeId) {
        Prospect p = new Prospect();
        p.setOrigem(OrigemProspect.GOOGLE);
        p.setPlaceId(placeId);
        p.setCidade(cidade);
        p.setUf("SP");
        p.setResponsavel(usuarioRepo.findByLogin("admin").orElseThrow());
        return p;
    }

    private ResultActions salvar(String token, String placeId) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"placeId":"%s","cidade":"%s","uf":"sp","categoria":"SUPERMERCADO"}
                        """.formatted(placeId, cidade)));
    }

    private ResultActions visita(long id, String corpo) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects/{id}/visita", id)
                .header(HttpHeaders.AUTHORIZATION, comercial)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions lto(String token, long id, String cnpj) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects/{id}/lto", id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cnpj":"%s","qtdServidores":1,"qtdPdvs":3,"qtdRetaguardas":1,"consultaPreco":false}
                        """.formatted(cnpj)));
    }

    private static long idDe(ResultActions resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String novoPlaceId() {
        return "ChIJ" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String novoCnpj() {
        return String.format("%014d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 99_999_999_999_999L));
    }

    private static BrasilApiCnpjResponse receita(String cnpj, boolean lucroReal) {
        return new BrasilApiCnpjResponse(cnpj, "Mercado Receita Ltda", "Mercado Receita", "Rua das Flores", "100",
                "Centro", "LIMEIRA", "SP", "1934440000", null, false, false, "DEMAIS",
                List.of(new BrasilApiCnpjResponse.RegimeTributario(2025, lucroReal ? "LUCRO REAL" : "LUCRO PRESUMIDO")));
    }

    private static LugarGoogle lugar(String placeId, String nome) {
        return new LugarGoogle(placeId, new LugarGoogle.Texto(nome), "Rua A, 10", new LugarGoogle.Localizacao(-22.5, -47.4),
                null, "OPERATIONAL", "https://maps/" + placeId);
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
