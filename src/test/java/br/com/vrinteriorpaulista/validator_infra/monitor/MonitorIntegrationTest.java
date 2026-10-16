package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.Execucao;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.AgenteOfflineScheduler;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.DiscordNotifier;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo de monitoramento ponta a ponta: cadastro do cliente, agente (token próprio), "Consultar agora" e alertas.
 */
@IntegrationTest
class MonitorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClienteMonitoradoRepository clienteRepo;

    @Autowired
    private AgenteOfflineScheduler offlineScheduler;

    @MockitoBean
    private DiscordNotifier discord;

    private String admin;
    private long cliente;
    private String agente;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Mercado " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", startsWith("vrip_")))
                .andExpect(jsonPath("$.cliente.situacao").value("AGUARDANDO"))
                .andReturn().getResponse().getContentAsString();
        cliente = ((Number) JsonPath.read(resposta, "$.cliente.id")).longValue();
        agente = "Agente " + JsonPath.read(resposta, "$.token");
    }

    // ---- Agente ----

    @Test
    void pollingSemPedidoResponde204ERegistraOContato() throws Exception {
        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .header("X-Agente-Versao", "2.0.0"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.versaoAgente").value("2.0.0"))
                .andExpect(jsonPath("$.cliente.ultimoContato").isNotEmpty());
    }

    @Test
    void resultadoAgendadoComFalhaViraSituacaoFalhaEAlerta() throws Exception {
        enviarResultado("AGENDADO", null, false);

        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.situacao").value("FALHA"))
                .andExpect(jsonPath("$.cliente.totalLojas").value(2))
                .andExpect(jsonPath("$.cliente.lojasComFalha").value(1))
                .andExpect(jsonPath("$.lojas", hasSize(2)))
                .andExpect(jsonPath("$.lojas[0].ok").value(true))
                .andExpect(jsonPath("$.lojas[1].ok").value(false))
                .andExpect(jsonPath("$.lojas[1].verificacoes[1].tipo").value("ENCERRAMENTO"))
                .andExpect(jsonPath("$.lojas[1].verificacoes[1].ok").value(false));
        mockMvc.perform(get("/api/monitor/clientes/{id}/historico", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].origem").value("AGENDADO"));

        verify(discord).resultado(argThat(c -> c.getId() == cliente), any(Execucao.class));
    }

    @Test
    void erroDoAgenteAoConsultarOBancoViraFalha() throws Exception {
        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origem":"AGENDADO","executadoEm":"2026-10-15T07:00:00-03:00",
                                 "erro":"Connection refused","lojas":[]}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.situacao").value("FALHA"))
                .andExpect(jsonPath("$.erro").value("Connection refused"))
                .andExpect(jsonPath("$.lojas", hasSize(0)));
        verify(discord).resultado(argThat(c -> c.getId() == cliente), argThat(e -> !e.getOk()));
    }

    @Test
    void consultarAgoraPassaPeloAgenteEConcluiSemAlerta() throws Exception {
        String pedido = mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        long pedidoId = ((Number) JsonPath.read(pedido, "$.id")).longValue();

        // Clique repetido não enfileira outro pedido
        mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.id").value(pedidoId));

        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value(pedidoId));
        mockMvc.perform(get("/api/monitor/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("ENTREGUE"));
        // Já entregue: o próximo polling não recebe de novo
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isNoContent());

        enviarResultado("SOB_DEMANDA", pedidoId, true);

        mockMvc.perform(get("/api/monitor/pedidos/{id}", pedidoId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.execucaoId").isNumber());
        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.situacao").value("OK"));
        verify(discord, never()).resultado(any(), any());
    }

    @Test
    void naoConcluiPedidoDeOutroCliente() throws Exception {
        String outro = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        long outroId = ((Number) JsonPath.read(outro, "$.cliente.id")).longValue();
        String pedido = mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", outroId)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString();
        long pedidoDoOutro = ((Number) JsonPath.read(pedido, "$.id")).longValue();

        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultado("SOB_DEMANDA", pedidoDoOutro, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agenteOfflineAlertaUmaVezEAvisaQuandoVolta() throws Exception {
        ClienteMonitorado c = clienteRepo.findById(cliente).orElseThrow();
        c.setUltimoContato(Instant.now().minus(Duration.ofHours(3)));
        clienteRepo.save(c);

        offlineScheduler.verificar();
        offlineScheduler.verificar();

        verify(discord).agenteOffline(argThat(x -> x.getId() == cliente));
        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.situacao").value("OFFLINE"));

        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isNoContent());

        verify(discord).agenteVoltou(argThat(x -> x.getId() == cliente));
        mockMvc.perform(get("/api/monitor/clientes/{id}", cliente).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cliente.situacao").value("AGUARDANDO"));
    }

    // ---- Autenticação e permissões ----

    @Test
    void tokenDoAgenteSoValeNosEndpointsDoAgente() throws Exception {
        mockMvc.perform(get("/api/monitor/agente/pedidos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, "Agente vrip_invalido"))
                .andExpect(status().isUnauthorized());
        // JWT de usuário (mesmo ADMIN) não se passa por agente
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden());
        // Token do agente não abre o resto da API
        mockMvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/monitor/clientes").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void novoTokenInvalidaOAnterior() throws Exception {
        String resposta = mockMvc.perform(post("/api/monitor/clientes/{id}/token", cliente)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String novo = "Agente " + JsonPath.read(resposta, "$.token");

        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/monitor/agente/pedidos").header(HttpHeaders.AUTHORIZATION, novo))
                .andExpect(status().isNoContent());
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        String suporte = token(usuario(Perfil.SUPORTE), "senha123");
        mockMvc.perform(get("/api/monitor/clientes").header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente).header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isAccepted());
        // Cadastro e token são só ADMIN/DIRETORIA
        mockMvc.perform(post("/api/monitor/clientes/{id}/token", cliente).header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isForbidden());

        String diretoria = token(usuario(Perfil.DIRETORIA), "senha123");
        mockMvc.perform(post("/api/monitor/clientes/{id}/token", cliente).header(HttpHeaders.AUTHORIZATION, diretoria))
                .andExpect(status().isOk());

        for (Perfil perfil : List.of(Perfil.COMERCIAL, Perfil.TECNICO, Perfil.PROJETOS)) {
            mockMvc.perform(get("/api/monitor/clientes")
                            .header(HttpHeaders.AUTHORIZATION, token(usuario(perfil), "senha123")))
                    .andExpect(status().isForbidden());
        }
    }

    // ---- Helpers ----

    private void enviarResultado(String origem, Long pedidoId, boolean tudoOk) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultado(origem, pedidoId, tudoOk)))
                .andExpect(status().isNoContent());
    }

    /** Duas lojas; com tudoOk=false, o encerramento da loja 2 falha. */
    private static String resultado(String origem, Long pedidoId, boolean tudoOk) {
        return """
                {"origem":"%s","pedidoId":%s,"executadoEm":"2026-10-15T07:00:00-03:00","versaoAgente":"2.0.0",
                 "lojas":[
                   {"lojaId":1,"cnpj":"11222333000181","descricao":"LOJA 1","verificacoes":[
                     {"tipo":"CONSISTENCIA","ok":true,"referencia":"2026-10-14"},
                     {"tipo":"ENCERRAMENTO","ok":true,"referencia":"2026-10-15"}]},
                   {"lojaId":2,"cnpj":"11222333000262","descricao":"LOJA 2","verificacoes":[
                     {"tipo":"CONSISTENCIA","ok":true,"referencia":"2026-10-14"},
                     {"tipo":"ENCERRAMENTO","ok":%s,"referencia":"2026-10-15"}]}
                 ]}
                """.formatted(origem, pedidoId, tudoOk);
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
        String login = "monitor." + perfil.name().toLowerCase();
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
