package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.DiscordNotifier;
import br.com.vrinteriorpaulista.validator_infra.monitor.service.LogContainerService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Log de container sob demanda: Suporte pede, agente busca no polling e devolve, texto apagado após a retenção.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LogContainerIntegrationTest {

    private static final String CONTAINER = "vr.pdv";
    private static final String SEGREDO_NO_LOG = "SENHA_DO_BANCO=segredo-" + UUID.randomUUID();

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
    private LogContainerService logService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @MockitoBean
    private DiscordNotifier discord;

    private String admin;
    private String suporte;
    private long cliente;
    private String agente;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        suporte = token(usuario(Perfil.SUPORTE), "senha123");
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Log " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        cliente = ((Number) JsonPath.read(resposta, "$.cliente.id")).longValue();
        agente = "Agente " + JsonPath.read(resposta, "$.token");
        enviarAmbiente("2.3.0");
    }

    @Test
    void fluxoCompletoSemTextoNoLogDoServidor(CapturedOutput output) throws Exception {
        long pedido = pedirLog(CONTAINER, 50);

        // Clique repetido no mesmo container devolve o mesmo pedido
        pedirLogResposta(CONTAINER, 50).andExpect(jsonPath("$.pedidoId").value(pedido));
        consultarLog(pedido).andExpect(jsonPath("$.status").value("PENDENTE"));

        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .header("X-Agente-Versao", "2.3.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value(pedido))
                .andExpect(jsonPath("$.tipo").value("LOG"))
                .andExpect(jsonPath("$.container").value(CONTAINER))
                .andExpect(jsonPath("$.linhas").value(50));
        consultarLog(pedido).andExpect(jsonPath("$.status").value("ENTREGUE"));

        enviarLog(pedido, "{\"texto\":\"linha 1\\n" + SEGREDO_NO_LOG + "\",\"truncado\":true}")
                .andExpect(status().isNoContent());

        consultarLog(pedido)
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.texto").value("linha 1\n" + SEGREDO_NO_LOG))
                .andExpect(jsonPath("$.truncado").value(true))
                .andExpect(jsonPath("$.erro").doesNotExist())
                .andExpect(jsonPath("$.recebidoEm").isNotEmpty());

        // Uma resposta por pedido
        enviarLog(pedido, "{\"texto\":\"outra\"}").andExpect(status().isConflict());

        // Auditoria: quem pediu, cliente, container, quando
        tx.executeWithoutResult(s -> {
            PedidoConsulta p = pedidoRepo.findById(pedido).orElseThrow();
            assertThat(p.getSolicitadoPor().getLogin()).isEqualTo("log.suporte");
            assertThat(p.getCliente().getId()).isEqualTo(cliente);
            assertThat(p.getContainer()).isEqualTo(CONTAINER);
            assertThat(p.getCriadoEm()).isNotNull();
        });
        assertThat(output.getAll())
                .contains("Log do container " + CONTAINER + " do cliente monitorado " + cliente)
                .doesNotContain(SEGREDO_NO_LOG);
    }

    @Test
    void agenteInformaErroOuTextoGrandeDemais() throws Exception {
        long pedido = pedirLog(CONTAINER, 200);

        // 140 mil "é" = 280 mil bytes em UTF-8: passa no @Size (caracteres), barra no limite de bytes
        enviarLog(pedido, "{\"texto\":\"" + "é".repeat(140_000) + "\"}").andExpect(status().isBadRequest());
        enviarLog(pedido, "{}").andExpect(status().isBadRequest());

        enviarLog(pedido, "{\"erro\":\"No such container: vr.pdv\"}").andExpect(status().isNoContent());
        consultarLog(pedido)
                .andExpect(jsonPath("$.status").value("ERRO"))
                .andExpect(jsonPath("$.erro").value("No such container: vr.pdv"))
                .andExpect(jsonPath("$.texto").doesNotExist())
                .andExpect(jsonPath("$.truncado").value(false));
    }

    @Test
    void containerInexistenteResponde404() throws Exception {
        pedirLogResposta("nao-existe", 200).andExpect(status().isNotFound());
        // Pedido de outro container não aparece por este
        long pedido = pedirLog(CONTAINER, 200);
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, "outro", pedido)
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteOfflineResponde409() throws Exception {
        ClienteMonitorado c = clienteRepo.findById(cliente).orElseThrow();
        c.setUltimoContato(Instant.now().minus(Duration.ofHours(3)));
        clienteRepo.save(c);

        pedirLogResposta(CONTAINER, 200).andExpect(status().isConflict());
    }

    @Test
    void linhasForaDoLimiteResponde400() throws Exception {
        pedirLogResposta(CONTAINER, 501).andExpect(status().isBadRequest());
        pedirLogResposta(CONTAINER, 0).andExpect(status().isBadRequest());
    }

    @Test
    void agente220NaoRecebeLogEContinuaRecebendoAConsulta() throws Exception {
        long log = pedirLog(CONTAINER, 200);
        long consulta = ((Number) JsonPath.read(mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        // O LOG é mais antigo, mas o 2.2.0 só entende a consulta: recebe ela, no contrato antigo
        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .header("X-Agente-Versao", "2.2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value(consulta))
                .andExpect(jsonPath("$.tipo").doesNotExist())
                .andExpect(jsonPath("$.container").doesNotExist())
                .andExpect(jsonPath("$.linhas").doesNotExist());
        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .header("X-Agente-Versao", "2.2.0"))
                .andExpect(status().isNoContent());
        consultarLog(log).andExpect(jsonPath("$.status").value("PENDENTE"));

        // Com o agente já em 2.2.0, nem aceita pedido novo de log
        pedirLogResposta("agente", 200).andExpect(status().isConflict());
    }

    @Test
    void pedidoDeLogExpiraEAgenteNaoRecebeMais() throws Exception {
        long pedido = pedirLog(CONTAINER, 200);
        jdbc.update("update monitor.pedido set criado_em = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))), pedido);

        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .header("X-Agente-Versao", "2.3.0"))
                .andExpect(status().isNoContent());
        consultarLog(pedido).andExpect(jsonPath("$.status").value("EXPIRADO"));
    }

    @Test
    void textoEApagadoDepoisDaRetencaoEAuditoriaFica() throws Exception {
        long antigo = pedirLog(CONTAINER, 200);
        enviarLog(antigo, "{\"texto\":\"" + SEGREDO_NO_LOG + "\"}").andExpect(status().isNoContent());
        long recente = pedirLog("agente", 200);
        enviarLog(recente, "{\"texto\":\"recente\"}").andExpect(status().isNoContent());
        jdbc.update("update monitor.pedido set concluido_em = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(61))), antigo);

        logService.limparTextos();

        consultarLog(antigo)
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.texto").doesNotExist())
                .andExpect(jsonPath("$.textoApagadoEm").isNotEmpty());
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, "agente", recente)
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(jsonPath("$.texto").value("recente"))
                .andExpect(jsonPath("$.textoApagadoEm").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from monitor.pedido where id = ? and texto is null"
                + " and container = ? and solicitado_por_id is not null", Long.class, antigo, CONTAINER))
                .isEqualTo(1L);
    }

    @Test
    void agenteNaoRespondePedidoDeOutroClienteNemConsultaComoLog() throws Exception {
        long consulta = ((Number) JsonPath.read(mockMvc.perform(post("/api/monitor/clientes/{id}/consultar", cliente)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        enviarLog(consulta, "{\"texto\":\"x\"}").andExpect(status().isBadRequest());

        long pedido = pedirLog(CONTAINER, 200);
        String outro = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro " + UUID.randomUUID() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/monitor/agente/pedidos/{id}/log", pedido)
                        .header(HttpHeaders.AUTHORIZATION, "Agente " + JsonPath.read(outro, "$.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"x\"}"))
                .andExpect(status().isBadRequest());
        // E o pedido de log não aparece como "Consultar agora"
        mockMvc.perform(get("/api/monitor/pedidos/{id}", pedido).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        long pedido = pedirLog(CONTAINER, 200);

        String diretoria = token(usuario(Perfil.DIRETORIA), "senha123");
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, CONTAINER, pedido)
                        .header(HttpHeaders.AUTHORIZATION, diretoria))
                .andExpect(status().isOk());

        for (Perfil perfil : new Perfil[]{Perfil.COMERCIAL, Perfil.TECNICO, Perfil.PROJETOS}) {
            String t = token(usuario(perfil), "senha123");
            mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/log", cliente, CONTAINER)
                            .header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, CONTAINER, pedido)
                            .header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
        }
        // O agente não usa os endpoints do Suporte
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, CONTAINER, pedido)
                        .header(HttpHeaders.AUTHORIZATION, agente))
                .andExpect(status().isUnauthorized());
    }

    // ---- Helpers ----

    private void enviarAmbiente(String versaoAgente) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/ambiente")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coletadoEm":"2026-11-06T08:00:00-03:00","versaoAgente":"%s","dockerDisponivel":true,
                                 "containers":[{"nome":"%s","versao":"4.2.10","rodando":true},
                                               {"nome":"agente","versao":"%s","rodando":true}]}
                                """.formatted(versaoAgente, CONTAINER, versaoAgente)))
                .andExpect(status().isNoContent());
    }

    private ResultActions pedirLogResposta(String container, int linhas) throws Exception {
        return mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/log", cliente, container)
                .param("linhas", String.valueOf(linhas))
                .header(HttpHeaders.AUTHORIZATION, suporte));
    }

    private long pedirLog(String container, int linhas) throws Exception {
        String resposta = pedirLogResposta(container, linhas)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.pedidoId")).longValue();
    }

    private ResultActions consultarLog(long pedido) throws Exception {
        return mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/log/{p}", cliente, CONTAINER, pedido)
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isOk());
    }

    private ResultActions enviarLog(long pedido, String corpo) throws Exception {
        return mockMvc.perform(post("/api/monitor/agente/pedidos/{id}/log", pedido)
                .header(HttpHeaders.AUTHORIZATION, agente)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
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
        String login = "log." + perfil.name().toLowerCase();
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
