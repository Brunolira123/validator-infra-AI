package br.com.vrinteriorpaulista.validator_infra.projetos;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.dto.brasilapi.BrasilApiCnpjResponse;
import br.com.vrinteriorpaulista.validator_infra.prospeccao.google.GooglePlacesClient;
import br.com.vrinteriorpaulista.validator_infra.service.brasilapi.BrasilApiClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo Projetos de ponta a ponta: LTO cria o rascunho, aplicar template, calcular (com a agenda de outros
 * projetos), carga, salvar como template e permissões. Cada teste usa analistas e clientes próprios porque o banco
 * é compartilhado pelo contexto. Mesmos mocks do ProspectIntegrationTest, para reaproveitar o contexto Spring.
 * 23/11/2026 é segunda-feira e a semana não tem feriado nacional.
 */
@IntegrationTest
class ProjetosIntegrationTest {

    private static final String SEGUNDA = "2026-11-23";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private GooglePlacesClient google;

    @MockitoBean
    private BrasilApiClient brasilApi;

    private String admin;
    private String projetos;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        projetos = usuario("PROJETOS").token();
    }

    @Test
    void ltoCriaPlanejamentoRascunhoQueSoOComercialQueFechouEnxerga() throws Exception {
        Usuario comercial = usuario("COMERCIAL");
        long prospectId = idDe(mockMvc.perform(post("/api/prospeccao/prospects")
                        .header(HttpHeaders.AUTHORIZATION, comercial.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":"ChIJ%s","cidade":"Limeira","uf":"SP","categoria":"SUPERMERCADO"}
                                """.formatted(UUID.randomUUID().toString().replace("-", ""))))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/visita", prospectId)
                        .header(HttpHeaders.AUTHORIZATION, comercial.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}"))
                .andExpect(status().isOk());
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj));
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/fechar", prospectId)
                        .header(HttpHeaders.AUTHORIZATION, comercial.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\"}"))
                .andExpect(status().isOk());
        String lto = mockMvc.perform(post("/api/prospeccao/prospects/{id}/lto", prospectId)
                        .header(HttpHeaders.AUTHORIZATION, comercial.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qtdServidores\":1,\"qtdPdvs\":2,\"qtdRetaguardas\":1,\"consultaPreco\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long clienteId = ((Number) JsonPath.read(lto, "$.clienteId")).longValue();
        long levantamentoId = ((Number) JsonPath.read(lto, "$.levantamentoId")).longValue();

        List<Long> ids = jdbc.queryForList("select id from projetos.planejamento where levantamento_id = ?",
                Long.class, levantamentoId);
        assertThat(ids).hasSize(1);
        long planejamentoId = ids.get(0);

        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("RASCUNHO"))
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.levantamentoId").value(levantamentoId))
                .andExpect(jsonPath("$.nome").value("Implantação Mercado Receita"))
                .andExpect(jsonPath("$.atividades").isEmpty())
                .andExpect(jsonPath("$.calculo").doesNotExist());

        // COMERCIAL que fechou: lê, mas não edita
        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, comercial.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/projetos/planejamentos").header(HttpHeaders.AUTHORIZATION, comercial.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planejamentos[*].id", hasItem((int) planejamentoId)));
        mockMvc.perform(put("/api/projetos/planejamentos/{id}", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, comercial.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro nome\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projetos/planejamentos/{id}/calcular", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, comercial.token()))
                .andExpect(status().isForbidden());

        // Outro COMERCIAL não enxerga (404, como inexistente) nem na lista
        String outro = usuario("COMERCIAL").token();
        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projetos/planejamentos").header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planejamentos[*].id", not(hasItem((int) planejamentoId))));
    }

    @Test
    void tecnicoNaoAcessaOModulo() throws Exception {
        String tecnico = usuario("TECNICO").token();

        mockMvc.perform(get("/api/projetos/planejamentos").header(HttpHeaders.AUTHORIZATION, tecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projetos/categorias").header(HttpHeaders.AUTHORIZATION, tecnico))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projetos/carga").param("de", SEGUNDA).param("ate", SEGUNDA)
                        .header(HttpHeaders.AUTHORIZATION, tecnico))
                .andExpect(status().isForbidden());
    }

    @Test
    void comercialNaoMexeNosCadastrosNemCriaPlanejamento() throws Exception {
        String comercial = usuario("COMERCIAL").token();
        long clienteId = cliente("Limeira");

        mockMvc.perform(get("/api/projetos/templates").header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projetos/carga").param("de", SEGUNDA).param("ate", SEGUNDA)
                        .header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projetos/planejamentos")
                        .header(HttpHeaders.AUTHORIZATION, comercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + clienteId + ",\"nome\":\"Avulso\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void seedTemCategoriasFeriadosComSextaFeiraSantaETemplatesDoFluxo() throws Exception {
        mockMvc.perform(get("/api/projetos/categorias").header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItems("Kickoff", "Conversão de banco", "Conferência de dados",
                        "Treinamento", "Implantação")))
                .andExpect(jsonPath("$[?(@.nome == 'Treinamento')].horasPadrao", hasItem(12.0)));
        mockMvc.perform(get("/api/projetos/feriados").param("ano", "2027").param("escopo", "NACIONAL")
                        .header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.descricao == 'Sexta-feira Santa')].data", hasItem("2027-03-26")))
                .andExpect(jsonPath("$[?(@.descricao == 'Dia Nacional de Zumbi e da Consciência Negra')].data",
                        hasItem("2027-11-20")));
        mockMvc.perform(get("/api/projetos/feriados").param("ano", "2028").header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(jsonPath("$[?(@.descricao == 'Sexta-feira Santa')].data", hasItem("2028-04-14")));
        mockMvc.perform(get("/api/projetos/templates").header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(jsonPath("$[*].nome", hasItems("Nova loja", "Implantação Master Fisco")));
    }

    @Test
    void aplicarTemplateCopiaHierarquiaEDependenciasECalcular() throws Exception {
        Usuario ana = usuario("PROJETOS");
        long planejamentoId = planejamento(cliente("Limeira"));

        String aplicado = aplicar(planejamentoId, templateId("Nova loja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("EM_PLANEJAMENTO"))
                .andExpect(jsonPath("$.templateNome").value("Nova loja"))
                .andExpect(jsonPath("$.templateVersao").isNumber())
                .andExpect(jsonPath("$.atividades.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        Map<String, Long> ids = idsPorNome(aplicado);
        long preparar = ids.get("Preparar nova loja");
        long receber = ids.get("Receber cadastro de produtos");
        long conferir = ids.get("Conferir equipamentos");
        long treinar = ids.get("Treinar equipe");
        assertThat(JsonPath.<List<Object>>read(aplicado, "$.atividades[?(@.id == " + conferir + ")].paiId"))
                .containsExactly((int) preparar);
        assertThat(JsonPath.<List<Object>>read(aplicado, "$.atividades[?(@.id == " + conferir + ")].dependeDe[0]"))
                .containsExactly((int) receber);
        assertThat(JsonPath.<List<Object>>read(aplicado, "$.atividades[?(@.id == " + treinar + ")].dependeDe[0]"))
                .containsExactly((int) preparar);
        assertThat(JsonPath.<List<Object>>read(aplicado, "$.atividades[*].responsavelId")).containsOnlyNulls();

        // Sem responsáveis: os erros voltam juntos
        calcular(planejamentoId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Planejamento inválido"))
                .andExpect(jsonPath("$.erros.length()").value(3))
                .andExpect(jsonPath("$.erros", hasItems(
                        "Atividade \"Receber cadastro de produtos\": sem responsável",
                        "Atividade \"Conferir equipamentos\": sem responsável",
                        "Atividade \"Treinar equipe\": sem responsável")));

        responsavel(planejamentoId, receber, "Receber cadastro de produtos", preparar, "4", ana.id());
        responsavel(planejamentoId, conferir, "Conferir equipamentos", preparar, "3", ana.id());
        responsavel(planejamentoId, treinar, "Treinar equipe", null, "8", ana.id());

        String calculo = calcular(planejamentoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value(SEGUNDA))
                .andExpect(jsonPath("$.termino").value("2026-11-25"))
                .andExpect(jsonPath("$.diasUteis").value(3))
                .andExpect(jsonPath("$.conflitos").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(numero(calculo, "$.horasServico")).isEqualTo(15.0);
        assertThat(numero(calculo, "$.custoInterno")).isEqualTo(15 * 100.0 + 0);
        assertThat(numero(calculo, "$.saldoContrato")).isEqualTo(5.0);
        assertThat(JsonPath.<List<String>>read(calculo, "$.atividades[?(@.atividadeId == " + conferir + ")].inicio"))
                .containsExactly("2026-11-24");
        assertThat(JsonPath.<List<Object>>read(calculo, "$.atividades[?(@.atividadeId == " + preparar + ")].resumo"))
                .containsExactly(true);

        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(jsonPath("$.calculo.termino").value("2026-11-25"))
                .andExpect(jsonPath("$.calculoDesatualizado").value(false));

        // Editar marca o cálculo como desatualizado; enviar exige cálculo em dia
        responsavel(planejamentoId, treinar, "Treinar equipe", null, "6", ana.id());
        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(jsonPath("$.calculoDesatualizado").value(true));
        situacao(planejamentoId, "ENVIADO").andExpect(status().isConflict());
        calcular(planejamentoId).andExpect(status().isOk());
        situacao(planejamentoId, "ENVIADO").andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ENVIADO"));
        situacao(planejamentoId, "RASCUNHO").andExpect(status().isConflict());
    }

    @Test
    void aplicarTemplateEmPlanejamentoComAtividadesExigeSubstituir() throws Exception {
        long planejamentoId = planejamento(cliente("Limeira"));
        aplicar(planejamentoId, templateId("Nova loja")).andExpect(status().isOk());

        aplicar(planejamentoId, templateId("Implantação Master Fisco")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/projetos/planejamentos/{id}/aplicar-template", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":" + templateId("Implantação Master Fisco") + ",\"substituir\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateNome").value("Implantação Master Fisco"))
                .andExpect(jsonPath("$.atividades.length()").value(4))
                .andExpect(jsonPath("$.atividades[*].nome", hasItem("Validar emissão com o cliente")));
    }

    @Test
    void calculoDescontaAgendaDeOutrosProjetosEListaConflitosECarga() throws Exception {
        Usuario bruno = usuario("PROJETOS");
        long primeiro = planejamento(cliente("Limeira"));
        situacao(primeiro, "EM_PLANEJAMENTO").andExpect(status().isOk());
        atividade(primeiro, "Conversão de banco", "16", bruno.id());
        calcular(primeiro).andExpect(status().isOk());

        // O segundo continua RASCUNHO: calcula descontando o primeiro, mas não ocupa a agenda de ninguém
        long segundo = planejamento(cliente("Piracicaba"));
        atividade(segundo, "Treinamento", "8", bruno.id());
        String calculo = calcular(segundo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-25"))
                .andExpect(jsonPath("$.conflitos.length()").value(1))
                .andExpect(jsonPath("$.conflitos[0].usuarioId").value(bruno.id()))
                .andExpect(jsonPath("$.conflitos[0].semana").value(SEGUNDA))
                .andExpect(jsonPath("$.conflitos[0].mensagem").value(bruno.nome() + ": 16h em outro projeto na semana de 23/11"))
                .andReturn().getResponse().getContentAsString();
        assertThat(numero(calculo, "$.conflitos[0].horas")).isEqualTo(16.0);

        String carga = mockMvc.perform(get("/api/projetos/carga").param("de", SEGUNDA).param("ate", "2026-11-27")
                        .header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> doBruno = JsonPath.read(carga, "$[?(@.usuarioId == " + bruno.id() + ")]");
        assertThat(doBruno).extracting(d -> d.get("data")).containsExactly(SEGUNDA, "2026-11-24");
        assertThat(doBruno).allSatisfy(d -> assertThat(((Number) d.get("horas")).doubleValue()).isEqualTo(8.0));
        assertThat(JsonPath.<List<Object>>read(carga, "$[?(@.usuarioId == " + bruno.id() + ")].planejamentos[*].planejamentoId"))
                .containsOnly((int) primeiro);

        mockMvc.perform(get("/api/projetos/carga").param("de", "2026-11-27").param("ate", SEGUNDA)
                        .header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atividadeDoClienteEFeriadoLocalDoMunicipio() throws Exception {
        Usuario ana = usuario("PROJETOS");
        String cidade = "Cidade " + UUID.randomUUID().toString().substring(0, 6);
        long clienteId = cliente(cidade);
        mockMvc.perform(post("/api/projetos/feriados")
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + SEGUNDA + "\",\"descricao\":\"Aniversário\",\"escopo\":\"LOCAL\",\"cidade\":\""
                                + cidade.toUpperCase() + "\",\"uf\":\"sp\"}"))
                .andExpect(status().isCreated());
        long contato = idDe(mockMvc.perform(post("/api/projetos/clientes/{id}/contatos", clienteId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Joana\",\"email\":\"joana@cliente.com\",\"setor\":\"Fiscal\"}"))
                .andExpect(status().isCreated()));
        long planejamentoId = planejamento(clienteId);
        long conferencia = idDe(mockMvc.perform(post("/api/projetos/planejamentos/{id}/atividades", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente confere cadastro\",\"doCliente\":true,\"diasCliente\":2,\"contatoClienteId\":"
                                + contato + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contatoClienteNome").value("Joana")));
        long ajustes = atividade(planejamentoId, "Ajustes", "4", ana.id());
        mockMvc.perform(put("/api/projetos/planejamentos/{id}/atividades/{a}/dependencias", planejamentoId, ajustes)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependeDe\":[" + conferencia + "]}"))
                .andExpect(status().isOk());

        // Segunda é feriado na cidade: o cliente confere terça e quarta, os ajustes ficam para quinta
        String calculo = calcular(planejamentoId).andExpect(status().isOk())
                .andExpect(jsonPath("$.termino").value("2026-11-26"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(calculo, "$.atividades[?(@.atividadeId == " + conferencia + ")].inicio"))
                .containsExactly("2026-11-24");
        assertThat(JsonPath.<List<String>>read(calculo, "$.atividades[?(@.atividadeId == " + conferencia + ")].fim"))
                .containsExactly("2026-11-25");
        assertThat(numero(calculo, "$.horasServico")).isEqualTo(4.0);
        assertThat(JsonPath.<List<Object>>read(calculo, "$.alocacoes[*].atividadeId")).containsOnly((int) ajustes);
    }

    @Test
    void deslocamentoEFeriadoExtraEntramNoCalculoECusto() throws Exception {
        Usuario ana = usuario("PROJETOS");
        long planejamentoId = planejamento(cliente("Limeira"));
        atividade(planejamentoId, "Treinamento", "12", ana.id());
        mockMvc.perform(post("/api/projetos/planejamentos/{id}/deslocamentos", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + ana.id() + ",\"data\":\"2026-11-24\",\"horas\":3,\"descricao\":\"Ida\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projetos/planejamentos/{id}/deslocamentos", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + ana.id() + ",\"data\":\"2026-11-24\",\"horas\":9}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/projetos/planejamentos/{id}/feriados-extras", planejamentoId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + SEGUNDA + "\",\"descricao\":\"Emenda\"}"))
                .andExpect(status().isCreated());

        // Segunda sem expediente; terça 3h de viagem + 5h de treinamento; quarta 7h
        String calculo = calcular(planejamentoId).andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-24"))
                .andExpect(jsonPath("$.termino").value("2026-11-25"))
                .andExpect(jsonPath("$.diasUteis").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(numero(calculo, "$.horasViagem")).isEqualTo(3.0);
        assertThat(numero(calculo, "$.custoInterno")).isEqualTo(12 * 100.0 + 3 * 50.0);
    }

    @Test
    void salvarPlanejamentoComoTemplateNovoOuNovaVersao() throws Exception {
        long planejamentoId = planejamento(cliente("Limeira"));
        aplicar(planejamentoId, templateId("Implantação Master Fisco")).andExpect(status().isOk());
        String nome = "Modelo " + UUID.randomUUID().toString().substring(0, 8);

        String novo = salvarComoTemplate(planejamentoId, "{\"nome\":\"" + nome + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.atividades.length()").value(4))
                .andExpect(jsonPath("$.atividades[?(@.nome == 'Configurar parâmetros fiscais')].dependeDe[0]", hasItem("a2")))
                .andExpect(jsonPath("$.atividades[?(@.nome == 'Configurar parâmetros fiscais')].chavePai", hasItem("a1")))
                .andReturn().getResponse().getContentAsString();
        long templateId = ((Number) JsonPath.read(novo, "$.id")).longValue();

        salvarComoTemplate(planejamentoId, "{\"nome\":\"" + nome + "\"}").andExpect(status().isConflict());
        salvarComoTemplate(planejamentoId, "{\"templateId\":" + templateId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(2))
                .andExpect(jsonPath("$.nome").value(nome));
    }

    @Test
    void excluirAtividadeLevaAsSubtarefasEAsDependencias() throws Exception {
        long planejamentoId = planejamento(cliente("Limeira"));
        Map<String, Long> ids = idsPorNome(aplicar(planejamentoId, templateId("Nova loja"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(delete("/api/projetos/planejamentos/{id}/atividades/{a}", planejamentoId, ids.get("Preparar nova loja"))
                        .header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projetos/planejamentos/{id}", planejamentoId).header(HttpHeaders.AUTHORIZATION, projetos))
                .andExpect(jsonPath("$.atividades.length()").value(1))
                .andExpect(jsonPath("$.atividades[0].nome").value("Treinar equipe"))
                .andExpect(jsonPath("$.atividades[0].dependeDe").isEmpty());
    }

    @Test
    void planejamentoCanceladoEhSomenteLeitura() throws Exception {
        long planejamentoId = planejamento(cliente("Limeira"));
        situacao(planejamentoId, "CANCELADO").andExpect(status().isOk());

        atividadeRequest(planejamentoId, "{\"nome\":\"Nova\",\"horas\":2}").andExpect(status().isConflict());
        calcular(planejamentoId).andExpect(status().isConflict());
        situacao(planejamentoId, "EM_PLANEJAMENTO").andExpect(status().isOk());
        atividadeRequest(planejamentoId, "{\"nome\":\"Nova\",\"horas\":2}").andExpect(status().isCreated());
    }

    @Test
    void bancoTemChecksDosEnumsDoModulo() {
        List<String> restricoes = jdbc.queryForList("""
                select pg_get_constraintdef(c.oid) from pg_constraint c
                join pg_class t on t.oid = c.conrelid join pg_namespace n on n.oid = t.relnamespace
                where n.nspname = 'projetos' and t.relname = 'planejamento' and c.contype = 'c'
                """, String.class);
        assertThat(restricoes).anyMatch(r -> r.contains("'CRIADO_ARTIA'") && r.contains("'CANCELADO'"));
    }

    // ----- apoio -----

    private record Usuario(long id, String nome, String token) {}

    private ResultActions calcular(long planejamentoId) throws Exception {
        return mockMvc.perform(post("/api/projetos/planejamentos/{id}/calcular", planejamentoId)
                .header(HttpHeaders.AUTHORIZATION, projetos));
    }

    private ResultActions situacao(long planejamentoId, String situacao) throws Exception {
        return mockMvc.perform(put("/api/projetos/planejamentos/{id}/situacao", planejamentoId)
                .header(HttpHeaders.AUTHORIZATION, projetos)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"situacao\":\"" + situacao + "\"}"));
    }

    private ResultActions aplicar(long planejamentoId, long templateId) throws Exception {
        return mockMvc.perform(post("/api/projetos/planejamentos/{id}/aplicar-template", planejamentoId)
                .header(HttpHeaders.AUTHORIZATION, projetos)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":" + templateId + "}"));
    }

    private ResultActions salvarComoTemplate(long planejamentoId, String corpo) throws Exception {
        return mockMvc.perform(post("/api/projetos/planejamentos/{id}/salvar-como-template", planejamentoId)
                .header(HttpHeaders.AUTHORIZATION, projetos)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions atividadeRequest(long planejamentoId, String corpo) throws Exception {
        return mockMvc.perform(post("/api/projetos/planejamentos/{id}/atividades", planejamentoId)
                .header(HttpHeaders.AUTHORIZATION, projetos)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private long atividade(long planejamentoId, String nome, String horas, long responsavelId) throws Exception {
        return idDe(atividadeRequest(planejamentoId,
                "{\"nome\":\"" + nome + "\",\"horas\":" + horas + ",\"responsavelId\":" + responsavelId + "}")
                .andExpect(status().isCreated()));
    }

    private void responsavel(long planejamentoId, long atividadeId, String nome, Long paiId, String horas,
                             long responsavelId) throws Exception {
        mockMvc.perform(put("/api/projetos/planejamentos/{id}/atividades/{a}", planejamentoId, atividadeId)
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"paiId\":" + paiId + ",\"horas\":" + horas
                                + ",\"responsavelId\":" + responsavelId + "}"))
                .andExpect(status().isOk());
    }

    private long planejamento(long clienteId) throws Exception {
        return idDe(mockMvc.perform(post("/api/projetos/planejamentos")
                        .header(HttpHeaders.AUTHORIZATION, projetos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":%d,"nome":"Implantação avulsa","kickoff":"%s","horasContratadas":20,
                                 "custoHoraServico":100,"custoHoraViagem":50}
                                """.formatted(clienteId, SEGUNDA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("RASCUNHO")));
    }

    private long templateId(String nome) throws Exception {
        String resposta = mockMvc.perform(get("/api/projetos/templates").header(HttpHeaders.AUTHORIZATION, projetos))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(resposta, "$[?(@.nome == '" + nome + "')].id");
        return ids.get(0).longValue();
    }

    private long cliente(String cidade) throws Exception {
        String resposta = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + novoCnpj() + "\",\"razaoSocial\":\"Mercado Teste Ltda\",\"cidade\":\""
                                + cidade + "\",\"uf\":\"SP\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private static Map<String, Long> idsPorNome(String planejamento) {
        List<Map<String, Object>> atividades = JsonPath.read(planejamento, "$.atividades");
        return atividades.stream().collect(java.util.stream.Collectors.toMap(
                a -> (String) a.get("nome"), a -> ((Number) a.get("id")).longValue()));
    }

    private static double numero(String json, String caminho) {
        return ((Number) JsonPath.read(json, caminho)).doubleValue();
    }

    private static long idDe(ResultActions resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String novoCnpj() {
        return String.format("%014d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 99_999_999_999_999L));
    }

    private static BrasilApiCnpjResponse receita(String cnpj) {
        return new BrasilApiCnpjResponse(cnpj, "Mercado Receita Ltda", "Mercado Receita", "Rua das Flores", "100",
                "Centro", "LIMEIRA", "SP", "1934440000", null, false, false, "DEMAIS",
                List.of(new BrasilApiCnpjResponse.RegimeTributario(2025, "LUCRO REAL")));
    }

    private Usuario usuario(String perfil) throws Exception {
        String login = "u." + UUID.randomUUID().toString().substring(0, 8);
        String nome = "Usuário " + login;
        String resposta = mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","nome":"%s","email":"%s@vr.com.br","perfil":"%s","senha":"senha-forte-1"}
                                """.formatted(login, nome, login, perfil)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Usuario(((Number) JsonPath.read(resposta, "$.id")).longValue(), nome, token(login, "senha-forte-1"));
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
