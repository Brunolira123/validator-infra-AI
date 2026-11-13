package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.StatusPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AgenteOfflineScheduler;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.DiscordNotifier;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desativar/reativar cliente e a lista única (GET /api/monitor/clientes). Cada teste cria clientes com um
 * prefixo único e filtra a lista por ele (busca), para não ver os de outros testes.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ListaClientesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClienteMonitoradoRepository clienteRepo;

    @Autowired
    private PedidoConsultaRepository pedidoRepo;

    @Autowired
    private AgenteOfflineScheduler offlineScheduler;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private DiscordNotifier discord;

    private String admin;
    private String prefixo;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        prefixo = "Lista " + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- Desativar / reativar ----

    @Test
    void desativarRevogaTokenExpiraPedidosEMantemHistorico(CapturedOutput output) throws Exception {
        Cliente c = cadastrar(prefixo + " Mercado");
        enviarResultado(c, "4.4.93", true);
        mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/agente/versao-alvo", c.id())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versao\":\"9.0.0\"}"))
                .andExpect(status().isNoContent());

        // Um pedido ENTREGUE (o agente já buscou) e um PENDENTE
        Long entregue = consultarAgora(c);
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, c.agente()))
                .andExpect(status().isOk());
        PedidoConsulta pendente = new PedidoConsulta();
        pendente.setCliente(clienteRepo.findById(c.id()).orElseThrow());
        pendente = pedidoRepo.save(pendente);

        desativar(c, "{\"motivo\":\"  Cliente cancelou o contrato  \"}").andExpect(status().isNoContent());

        // Agente e vrip-updater recusados
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, c.agente()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/monitor/agente/atualizacao").header(HttpHeaders.AUTHORIZATION, c.agente()))
                .andExpect(status().isUnauthorized());
        enviarResultadoSemChecar(c, "4.4.93").andExpect(status().isUnauthorized());

        assertThat(pedidoRepo.findById(entregue).orElseThrow().getStatus()).isEqualTo(StatusPedido.EXPIRADO);
        assertThat(pedidoRepo.findById(pendente.getId()).orElseThrow().getStatus()).isEqualTo(StatusPedido.EXPIRADO);
        ClienteMonitorado salvo = clienteRepo.findById(c.id()).orElseThrow();
        assertThat(salvo.getAtivo()).isFalse();
        assertThat(salvo.getVersaoAlvoAgente()).isNull();

        // Histórico intacto
        mockMvc.perform(get("/api/monitor/clientes/{id}/historico", c.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Some da lista padrão e aparece com inativos=true
        listar("busca", prefixo).andExpect(jsonPath("$", hasSize(0)));
        listar("busca", prefixo, "inativos", "true")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ativo").value(false))
                .andExpect(jsonPath("$[0].situacao").value("INATIVO"))
                .andExpect(jsonPath("$[0].versaoBanco").value("4.4.93"))
                .andExpect(jsonPath("$[0].versaoAlvoAgente").doesNotExist())
                .andExpect(jsonPath("$[0].desativacao.em").isNotEmpty())
                .andExpect(jsonPath("$[0].desativacao.porNome").value(usuarioRepo.findByLogin("admin").orElseThrow().getNome()))
                .andExpect(jsonPath("$[0].desativacao.motivo").value("Cliente cancelou o contrato"));

        assertThat(output.getAll()).containsPattern("INFO.*Cliente monitorado " + c.id() + " \\(" + prefixo
                + " Mercado\\) desativado por admin em .*; motivo: Cliente cancelou o contrato; 2 pedido\\(s\\)");
    }

    @Test
    void desativarSemCorpoEMotivoGrande() throws Exception {
        Cliente c = cadastrar(prefixo + " Sem motivo");
        desativar(c, "{\"motivo\":\"" + "x".repeat(301) + "\"}").andExpect(status().isBadRequest());
        assertThat(clienteRepo.findById(c.id()).orElseThrow().getAtivo()).isTrue();

        mockMvc.perform(post("/api/monitor/clientes/{id}/desativar", c.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
        listar("busca", prefixo, "inativos", "true")
                .andExpect(jsonPath("$[0].desativacao.motivo").doesNotExist());
    }

    @Test
    void agenteDesativadoNaoGeraAlertaDeOffline() throws Exception {
        Cliente c = cadastrar(prefixo + " Offline");
        ClienteMonitorado m = clienteRepo.findById(c.id()).orElseThrow();
        m.setUltimoContato(Instant.now().minus(Duration.ofHours(3)));
        clienteRepo.save(m);

        desativar(c, null).andExpect(status().isNoContent());
        offlineScheduler.verificar();

        verify(discord, never()).agenteOffline(argThat(x -> x.getId().equals(c.id())));
    }

    @Test
    void reativarDevolveTokenNovoEOAntigoSegueRecusado() throws Exception {
        Cliente c = cadastrar(prefixo + " Volta");
        desativar(c, "{\"motivo\":\"teste\"}").andExpect(status().isNoContent());

        String resposta = mockMvc.perform(post("/api/monitor/clientes/{id}/reativar", c.id())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", startsWith("vrip_")))
                .andExpect(jsonPath("$.cliente.id").value(c.id()))
                .andExpect(jsonPath("$.cliente.ativo").value(true))
                .andExpect(jsonPath("$.cliente.situacao").value("AGUARDANDO"))
                .andReturn().getResponse().getContentAsString();
        String novo = "Agente " + JsonPath.read(resposta, "$.token");

        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, novo))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/monitor/agente/atualizacao").header(HttpHeaders.AUTHORIZATION, novo))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, c.agente()))
                .andExpect(status().isUnauthorized());

        listar("busca", prefixo)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].desativacao").doesNotExist());
        ClienteMonitorado salvo = clienteRepo.findById(c.id()).orElseThrow();
        assertThat(salvo.getDesativadoEm()).isNull();
        assertThat(salvo.getMotivoDesativacao()).isNull();
    }

    @Test
    void conflitosAoDesativarOuReativarDeNovo() throws Exception {
        Cliente c = cadastrar(prefixo + " Conflito");
        mockMvc.perform(post("/api/monitor/clientes/{id}/reativar", c.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
        desativar(c, null).andExpect(status().isNoContent());
        desativar(c, null).andExpect(status().isConflict());
        // Desativado não recebe "Consultar agora" nem versão alvo
        mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", c.id()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/agente/versao-alvo", c.id())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versao\":\"9.0.0\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/monitor/clientes/{id}/desativar", 999_999_999L).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void soAdminEDiretoriaDesativamEReativam() throws Exception {
        Cliente c = cadastrar(prefixo + " Permissao");
        String suporte = token(usuario(Perfil.SUPORTE), "senha123");
        String diretoria = token(usuario(Perfil.DIRETORIA), "senha123");

        mockMvc.perform(post("/api/monitor/clientes/{id}/desativar", c.id()).header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/monitor/clientes/{id}/desativar", c.id()).header(HttpHeaders.AUTHORIZATION, diretoria))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/monitor/clientes/{id}/reativar", c.id()).header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isForbidden());

        // SUPORTE lê a lista, inclusive os inativos, e as versões do banco
        mockMvc.perform(get("/api/monitor/clientes").param("busca", prefixo).param("inativos", "true")
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/monitor/versoes-banco").header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isOk());
        for (Perfil perfil : List.of(Perfil.COMERCIAL, Perfil.TECNICO, Perfil.PROJETOS)) {
            String t = token(usuario(perfil), "senha123");
            mockMvc.perform(get("/api/monitor/versoes-banco").header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/monitor/clientes/{id}/reativar", c.id()).header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/api/monitor/clientes/{id}/reativar", c.id()).header(HttpHeaders.AUTHORIZATION, diretoria))
                .andExpect(status().isOk());
    }

    // ---- Lista única ----

    @Test
    void itemTrazMonitoramentoESuporteJuntos() throws Exception {
        Cliente c = cadastrar(prefixo + " Completo");
        enviarResultado(c, "4.4.93", false);
        enviarAmbiente(c, false);
        mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/piloto", c.id())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"piloto\":true}"))
                .andExpect(status().isNoContent());
        Cliente aguardando = cadastrar(prefixo + " Nunca enviou");

        listar("busca", prefixo)
                .andExpect(jsonPath("$[*].id").value(contains(c.id().intValue(), aguardando.id().intValue())))
                .andExpect(jsonPath("$[0].situacao").value("FALHA"))
                .andExpect(jsonPath("$[0].ultimoContato").isNotEmpty())
                .andExpect(jsonPath("$[0].versaoAgente").value("2.4.0"))
                .andExpect(jsonPath("$[0].versaoBanco").value("4.4.93"))
                .andExpect(jsonPath("$[0].totalLojas").value(2))
                .andExpect(jsonPath("$[0].lojasComFalha").value(1))
                .andExpect(jsonPath("$[0].hoje.ok").value(false))
                .andExpect(jsonPath("$[0].hoje.totalLojas").value(2))
                .andExpect(jsonPath("$[0].hoje.lojasComFalha").value(1))
                .andExpect(jsonPath("$[0].hoje.verificacoes[*].tipo").value(contains("CONSISTENCIA", "ENCERRAMENTO")))
                .andExpect(jsonPath("$[0].hoje.verificacoes[0].lojas").value(2))
                .andExpect(jsonPath("$[0].hoje.verificacoes[0].falhas").value(0))
                .andExpect(jsonPath("$[0].hoje.verificacoes[1].falhas").value(1))
                .andExpect(jsonPath("$[0].ambiente.dockerDisponivel").value(true))
                .andExpect(jsonPath("$[0].ambiente.total").value(2))
                .andExpect(jsonPath("$[0].ambiente.rodando").value(1))
                .andExpect(jsonPath("$[0].ambiente.parados").value(1))
                .andExpect(jsonPath("$[0].ambiente.containers").doesNotExist())
                .andExpect(jsonPath("$[0].piloto").value(true))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].desativacao").doesNotExist())
                .andExpect(jsonPath("$[1].situacao").value("AGUARDANDO"))
                .andExpect(jsonPath("$[1].hoje").doesNotExist())
                .andExpect(jsonPath("$[1].ambiente").doesNotExist())
                .andExpect(jsonPath("$[1].piloto").value(false));
    }

    @Test
    void execucaoDeOntemNaoEntraNoResumoDeHoje() throws Exception {
        Cliente c = cadastrar(prefixo + " Ontem");
        enviarResultado(c, "4.4.93", true);
        jdbc.update("update monitor.execucao set recebido_em = recebido_em - interval '2 days' where cliente_id = ?", c.id());

        listar("busca", prefixo)
                .andExpect(jsonPath("$[0].situacao").value("OK"))
                .andExpect(jsonPath("$[0].ultimaExecucaoEm").isNotEmpty())
                .andExpect(jsonPath("$[0].hoje").doesNotExist());
    }

    @Test
    void filtrosDeVersaoDoBancoEmOrdemNumerica() throws Exception {
        Cliente v93 = cadastrar(prefixo + " A 93");
        Cliente v100 = cadastrar(prefixo + " B 100");
        Cliente v9 = cadastrar(prefixo + " C 9");
        Cliente outro9 = cadastrar(prefixo + " D 9");
        Cliente sem = cadastrar(prefixo + " E sem versão");
        Cliente inativo = cadastrar(prefixo + " F inativo");
        enviarResultado(v93, "7.7.93", true);
        enviarResultado(v100, "7.7.100", true);
        enviarResultado(v9, "7.7.9", true);
        enviarResultado(outro9, "7.7.9", true);
        enviarResultado(inativo, "7.7.9", true);
        desativar(inativo, null).andExpect(status().isNoContent());

        listar("busca", prefixo, "versaoBanco", "7.7.93", "versaoBanco", "7.7.100")
                .andExpect(jsonPath("$[*].id").value(contains(v93.id().intValue(), v100.id().intValue())));
        listar("busca", prefixo, "semVersaoBanco", "true")
                .andExpect(jsonPath("$[*].id").value(contains(sem.id().intValue())));
        listar("busca", prefixo, "versaoBanco", "7.7.100", "semVersaoBanco", "true")
                .andExpect(jsonPath("$[*].id").value(contains(v100.id().intValue(), sem.id().intValue())));
        listar("busca", prefixo, "versaoBanco", "7.7.9", "inativos", "true")
                .andExpect(jsonPath("$[*].id").value(contains(v9.id().intValue(), outro9.id().intValue(), inativo.id().intValue())));

        // Só ativos; 7.7.9 < 7.7.93 < 7.7.100
        String versoes = mockMvc.perform(get("/api/monitor/versoes-banco").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> nossas = JsonPath.read(versoes, "$[?(@.versao =~ /7\\.7\\..*/)].versao");
        List<Integer> clientes = JsonPath.read(versoes, "$[?(@.versao =~ /7\\.7\\..*/)].clientes");
        assertThat(nossas).containsExactly("7.7.9", "7.7.93", "7.7.100");
        assertThat(clientes).containsExactly(2, 1, 1);
        List<Object> nulas = JsonPath.read(versoes, "$[?(@.versao == null)]");
        assertThat(nulas).isEmpty();
    }

    @Test
    void filtrosCombinados() throws Exception {
        Cliente alvo = cadastrar(prefixo + " A alvo");
        Cliente atualizado = cadastrar(prefixo + " B atualizado");
        Cliente semPiloto = cadastrar(prefixo + " C sem piloto");
        Cliente ok = cadastrar(prefixo + " D ok");
        enviarResultado(alvo, "4.4.93", false);
        enviarResultado(atualizado, "4.4.93", false);
        enviarResultado(semPiloto, "4.4.93", false);
        enviarResultado(ok, "4.4.93", true);
        enviarAmbiente(alvo, false);
        enviarAmbiente(atualizado, false);
        enviarAmbiente(semPiloto, false);
        enviarAmbiente(ok, false);
        for (Cliente c : List.of(alvo, atualizado, ok)) {
            mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/piloto", c.id())
                            .header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"piloto\":true}"))
                    .andExpect(status().isNoContent());
        }
        // A maior versão em uso passa a ser a do "atualizado"; agente que nunca informou versão não conta
        versaoAgente(atualizado, "900.0.0");
        versaoAgente(alvo, "900.0.0-rc.1");
        versaoAgente(semPiloto, "899.9.9");
        versaoAgente(ok, "2.4.0");

        listar("busca", prefixo, "agenteDesatualizado", "true")
                .andExpect(jsonPath("$[*].id").value(contains(alvo.id().intValue(), semPiloto.id().intValue(), ok.id().intValue())));
        listar("busca", prefixo, "situacao", "FALHA", "comParado", "true", "piloto", "true", "agenteDesatualizado", "true")
                .andExpect(jsonPath("$[*].id").value(contains(alvo.id().intValue())));
        listar("busca", prefixo, "situacao", "OK", "situacao", "FALHA", "piloto", "false")
                .andExpect(jsonPath("$[*].id").value(contains(semPiloto.id().intValue())));
        listar("busca", prefixo.toUpperCase().replace("LISTA", "LÍSTÂ"), "situacao", "OK", "versaoBanco", "4.4.93")
                .andExpect(jsonPath("$[*].id").value(contains(ok.id().intValue())));
        listar("busca", prefixo, "situacao", "OFFLINE")
                .andExpect(jsonPath("$", hasSize(0)));

        // Não deixa a versão 900 influenciar outros testes
        for (Cliente c : List.of(alvo, atualizado, semPiloto, ok)) {
            desativar(c, null).andExpect(status().isNoContent());
        }
    }

    @Test
    void endpointAntigoDoSuporteContinuaFuncionando() throws Exception {
        Cliente c = cadastrar(prefixo + " Antigo");
        enviarAmbiente(c, false);
        mockMvc.perform(get("/api/monitor/suporte/clientes").param("busca", prefixo)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].situacao").value("ONLINE"))
                .andExpect(jsonPath("$[0].ambiente.containers", hasSize(2)));
        desativar(c, null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/monitor/suporte/clientes").param("busca", prefixo)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Helpers ----

    private record Cliente(Long id, String agente) {}

    private Cliente cadastrar(String nome) throws Exception {
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Cliente(((Number) JsonPath.read(resposta, "$.cliente.id")).longValue(),
                "Agente " + JsonPath.read(resposta, "$.token"));
    }

    private ResultActions desativar(Cliente c, String corpo) throws Exception {
        var req = post("/api/monitor/clientes/{id}/desativar", c.id()).header(HttpHeaders.AUTHORIZATION, admin);
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(req);
    }

    private Long consultarAgora(Cliente c) throws Exception {
        String resposta = mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", c.id())
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private void versaoAgente(Cliente c, String versao) {
        ClienteMonitorado m = clienteRepo.findById(c.id()).orElseThrow();
        m.setVersaoAgente(versao);
        clienteRepo.save(m);
    }

    private void enviarAmbiente(Cliente c, boolean tudoRodando) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/ambiente")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coletadoEm":"2026-11-06T08:00:00-03:00","dockerDisponivel":true,
                                 "containers":[{"nome":"vr-pdv","versao":"4.4.93","rodando":true},
                                               {"nome":"vr-retaguarda","versao":"4.4.93","rodando":%s}]}
                                """.formatted(tudoRodando)))
                .andExpect(status().isNoContent());
    }

    /** Duas lojas; com tudoOk=false, o encerramento da loja 2 falha. */
    private void enviarResultado(Cliente c, String versaoBanco, boolean tudoOk) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultado(versaoBanco, tudoOk)))
                .andExpect(status().isNoContent());
    }

    private ResultActions enviarResultadoSemChecar(Cliente c, String versaoBanco) throws Exception {
        return mockMvc.perform(post("/api/monitor/agente/resultados")
                .header(HttpHeaders.AUTHORIZATION, c.agente())
                .contentType(MediaType.APPLICATION_JSON)
                .content(resultado(versaoBanco, true)));
    }

    private static String resultado(String versaoBanco, boolean tudoOk) {
        return """
                {"origem":"SOB_DEMANDA","executadoEm":"2026-11-13T07:00:00-03:00","versaoAgente":"2.4.0",
                 "versaoBanco":"%s",
                 "lojas":[
                   {"lojaId":1,"cnpj":"11222333000181","descricao":"LOJA 1","verificacoes":[
                     {"tipo":"CONSISTENCIA","ok":true,"referencia":"2026-11-12"},
                     {"tipo":"ENCERRAMENTO","ok":true,"referencia":"2026-11-13"}]},
                   {"lojaId":2,"cnpj":"11222333000262","descricao":"LOJA 2","verificacoes":[
                     {"tipo":"CONSISTENCIA","ok":true,"referencia":"2026-11-12"},
                     {"tipo":"ENCERRAMENTO","ok":%s,"referencia":"2026-11-13"}]}
                 ]}
                """.formatted(versaoBanco, tudoOk);
    }

    /** Pares nome/valor de parâmetros (nome repetido = parâmetro com vários valores). */
    private ResultActions listar(String... params) throws Exception {
        var req = get("/api/monitor/clientes").header(HttpHeaders.AUTHORIZATION, admin);
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req).andExpect(status().isOk());
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
        String login = "lista." + perfil.name().toLowerCase();
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
}
