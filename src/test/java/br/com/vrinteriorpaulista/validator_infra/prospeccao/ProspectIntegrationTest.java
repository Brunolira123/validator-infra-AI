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

import java.time.LocalDate;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Funil do prospect: salvar, visita, fechamento, perda, LTO, descarte e visibilidade por usuário. Google e BrasilAPI mockados.
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
    void fecharAntesDaVisitaDa409() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        fechar(comercial, id, novoCnpj()).andExpect(status().isConflict());
    }

    @Test
    void fecharDepoisDeNaoAtendeuDa409() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}");

        fechar(comercial, id, novoCnpj()).andExpect(status().isConflict());
    }

    @Test
    void fecharCriaClienteComDadosDaReceita() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"MORNA\"}");
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));

        String resposta = fechar(comercial, id, cnpj).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.fechadoEm").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.perfil.foraDoPerfil").value(false))
                .andReturn().getResponse().getContentAsString();
        long clienteId = ((Number) JsonPath.read(resposta, "$.clienteId")).longValue();

        mockMvc.perform(get("/api/clientes/{id}", clienteId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cnpj").value(cnpj))
                .andExpect(jsonPath("$.razaoSocial").value("Mercado Receita Ltda"));
        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.status").value("FECHADO"))
                .andExpect(jsonPath("$.clienteId").value(clienteId));
        assertThat(jdbc.queryForObject("select count(*) from levantamento where cliente_id = ?", Long.class, clienteId))
                .isZero();

        // Contrato fechado não volta atrás
        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}").andExpect(status().isConflict());
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isConflict());
        perder(id, "{\"motivo\":\"PRECO\"}").andExpect(status().isConflict());
    }

    @Test
    void fecharComCnpjJaCadastradoReaproveitaOCliente() throws Exception {
        String cnpj = novoCnpj();
        long clienteExistente = clienteCadastrado(cnpj, "Já Cadastrado Ltda");
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, false));

        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}");

        mockMvc.perform(post("/api/prospeccao/prospects/{id}/fechar", id)
                        .header(HttpHeaders.AUTHORIZATION, comercial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cnpj":"%s","dataFechamento":"2026-10-01","observacao":"Assinou na loja"}
                                """.formatted(cnpj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteExistente))
                .andExpect(jsonPath("$.fechadoEm").value("2026-10-01"))
                .andExpect(jsonPath("$.observacao").value("Assinou na loja"))
                .andExpect(jsonPath("$.perfil.foraDoPerfil").value(true))
                .andExpect(jsonPath("$.perfil.motivo").value("Lucro presumido em 2025"));
        // Só para o selo: o cliente já cadastrado não é sobrescrito com a Receita
        verify(brasilApi, times(1)).consultarCnpj(cnpj);
        mockMvc.perform(get("/api/clientes/{id}", clienteExistente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.razaoSocial").value("Já Cadastrado Ltda"));
    }

    @Test
    void fecharComClienteCadastradoEReceitaForaDoArVemSemPerfil() throws Exception {
        String cnpj = novoCnpj();
        long clienteExistente = clienteCadastrado(cnpj, "Já Cadastrado Ltda");
        when(brasilApi.consultarCnpj(cnpj)).thenThrow(new ServicoIndisponivelException("BrasilAPI indisponível", null));

        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}");

        fechar(comercial, id, cnpj).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteExistente))
                .andExpect(jsonPath("$.perfil").doesNotExist());
    }

    @Test
    void fecharComCnpjDeClienteArquivadoReativaOMesmoCliente() throws Exception {
        String cnpj = novoCnpj();
        long arquivado = clienteCadastrado(cnpj, "Antigo Ltda");
        mockMvc.perform(delete("/api/clientes/{id}", arquivado).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));

        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"MORNA\"}");

        fechar(comercial, id, cnpj).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(arquivado));
        mockMvc.perform(get("/api/clientes/{id}", arquivado).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razaoSocial").value("Mercado Receita Ltda"));
    }

    @Test
    void ltoSemFecharDa409() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        lto(comercial, id).andExpect(status().isConflict());

        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}");
        lto(comercial, id).andExpect(status().isConflict());
    }

    @Test
    void ltoDepoisDoFechamentoCriaLevantamentoDoCliente() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}");
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));
        long clienteId = ((Number) JsonPath.read(fechar(comercial, id, cnpj).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.clienteId")).longValue();

        String resposta = lto(comercial, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andReturn().getResponse().getContentAsString();
        long levantamentoId = ((Number) JsonPath.read(resposta, "$.levantamentoId")).longValue();

        mockMvc.perform(get("/api/levantamentos/{id}", levantamentoId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.qtdPdvs").value(3))
                .andExpect(jsonPath("$.status").value("RASCUNHO"));
        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.status").value("LTO"))
                .andExpect(jsonPath("$.clienteId").value(clienteId));

        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}").andExpect(status().isConflict());
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isConflict());
        lto(comercial, id).andExpect(status().isConflict());
    }

    @Test
    void tecnicoNaoFechaNemFazLto() throws Exception {
        String tecnico = usuario("TECNICO");
        long id = idDe(salvar(tecnico, novoPlaceId()).andExpect(status().isCreated()));
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/visita", id)
                        .header(HttpHeaders.AUTHORIZATION, tecnico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}"))
                .andExpect(status().isOk());

        fechar(tecnico, id, novoCnpj()).andExpect(status().isForbidden());
        lto(tecnico, id).andExpect(status().isForbidden());
    }

    @Test
    void perderComMotivoEVisitarDeNovoReabre() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));
        perder(id, "{\"motivo\":\"PRECO\"}").andExpect(status().isConflict());
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"MORNA\"}");

        perder(id, "{}").andExpect(status().isBadRequest());
        perder(id, "{\"motivo\":\"CONCORRENTE\",\"observacao\":\"Fechou com o concorrente\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PERDIDO"))
                .andExpect(jsonPath("$.motivoPerda").value("CONCORRENTE"))
                .andExpect(jsonPath("$.observacaoPerda").value("Fechou com o concorrente"));
        mockMvc.perform(get("/api/prospeccao/prospects").param("cidade", cidade).param("status", "PERDIDO")
                        .header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(jsonPath("$.prospects[*].id", hasItem((int) id)));

        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISITADO"))
                .andExpect(jsonPath("$.motivoPerda").doesNotExist())
                .andExpect(jsonPath("$.observacaoPerda").doesNotExist());
    }

    @Test
    void perdidoFicaLivreParaOutroComercial() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\"}");
        perder(id, "{\"motivo\":\"SEM_INTERESSE\"}").andExpect(status().isOk());
        String outro = usuario("COMERCIAL");

        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(List.of(lugar(placeId, "Mercado")), null));
        buscar(outro)
                .andExpect(jsonPath("$.lugares[0].deOutroComercial").value(false))
                .andExpect(jsonPath("$.lugares[0].prospectId").doesNotExist());

        salvar(outro, placeId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("NOVO"))
                .andExpect(jsonPath("$.resultadoVisita").doesNotExist())
                .andExpect(jsonPath("$.motivoPerda").doesNotExist());
        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isNotFound());
    }

    @Test
    void fechadoContinuaDeOutroComercialNaBusca() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"QUENTE\"}");
        String cnpj = novoCnpj();
        when(brasilApi.consultarCnpj(cnpj)).thenReturn(receita(cnpj, true));
        fechar(comercial, id, cnpj).andExpect(status().isOk());
        String outro = usuario("COMERCIAL");

        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(List.of(lugar(placeId, "Mercado")), null));
        buscar(outro)
                .andExpect(jsonPath("$.lugares[0].deOutroComercial").value(true))
                .andExpect(jsonPath("$.lugares[0].statusProspect").doesNotExist());
        salvar(outro, placeId).andExpect(status().isConflict());
    }

    @Test
    void descartar() throws Exception {
        long id = idDe(salvar(comercial, novoPlaceId()));

        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCARTADO"));
    }

    @Test
    void descartadoPorUmFicaLivreEOutroAssume() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        visita(id, "{\"resultado\":\"ATENDEU\",\"temperatura\":\"FRIA\",\"observacao\":\"Sem interesse\"}")
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk());
        String outro = usuario("COMERCIAL");

        when(google.buscarTexto(anyString(), isNull())).thenReturn(new PaginaGoogle(List.of(lugar(placeId, "Mercado")), null));
        mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lugares[0].deOutroComercial").value(false))
                .andExpect(jsonPath("$.lugares[0].prospectId").doesNotExist());

        salvar(outro, placeId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("NOVO"))
                .andExpect(jsonPath("$.resultadoVisita").doesNotExist())
                .andExpect(jsonPath("$.temperatura").doesNotExist())
                .andExpect(jsonPath("$.visitadoEm").doesNotExist())
                .andExpect(jsonPath("$.observacao").doesNotExist());

        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, outro))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/prospeccao/prospects/{id}", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isNotFound());
        salvar(comercial, placeId).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from prospect_visita where prospect_id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void descartadoDoProprioUsuarioReabreComoNovo() throws Exception {
        String placeId = novoPlaceId();
        long id = idDe(salvar(comercial, placeId));
        visita(id, "{\"resultado\":\"NAO_ATENDEU\"}").andExpect(status().isOk());
        mockMvc.perform(post("/api/prospeccao/prospects/{id}/descartar", id).header(HttpHeaders.AUTHORIZATION, comercial))
                .andExpect(status().isOk());

        salvar(comercial, placeId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("NOVO"))
                .andExpect(jsonPath("$.resultadoVisita").doesNotExist());
        // Já reaberto: salvar de novo é o idempotente de sempre
        salvar(comercial, placeId).andExpect(status().isOk());
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
        assertThat(restricoes).anyMatch(r -> r.contains("'NOVO'") && r.contains("'VISITADO'") && r.contains("'FECHADO'")
                && r.contains("'LTO'") && r.contains("'PERDIDO'") && r.contains("'DESCARTADO'"));
        assertThat(restricoes).anyMatch(r -> r.contains("motivo_perda") && r.contains("'PRECO'") && r.contains("'CONCORRENTE'")
                && r.contains("'SEM_INTERESSE'") && r.contains("'OUTRO'"));
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

    private ResultActions fechar(String token, long id, String cnpj) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects/{id}/fechar", id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cnpj\":\"" + cnpj + "\"}"));
    }

    private ResultActions perder(long id, String corpo) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects/{id}/perder", id)
                .header(HttpHeaders.AUTHORIZATION, comercial)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private ResultActions lto(String token, long id) throws Exception {
        return mockMvc.perform(post("/api/prospeccao/prospects/{id}/lto", id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"qtdServidores":1,"qtdPdvs":3,"qtdRetaguardas":1,"consultaPreco":false}
                        """));
    }

    private ResultActions buscar(String token) throws Exception {
        return mockMvc.perform(get("/api/prospeccao/busca").param("cidade", cidade).param("uf", "SP")
                        .param("categorias", "SUPERMERCADO")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    private long clienteCadastrado(String cnpj, String razaoSocial) throws Exception {
        String resposta = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"" + razaoSocial + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
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
