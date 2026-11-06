package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.PedidoConsulta;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.TipoPedido;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.PedidoConsultaRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * START/RESTART de container pela tela de Suporte, entregue ao agente 2.4.0+ pela fila de pedidos.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AcaoContainerIntegrationTest {

    private static final String RODANDO = "vr-pdv";
    private static final String PARADO = "vr.retaguarda";

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
    private TransactionTemplate tx;

    @MockitoBean
    private DiscordNotifier discord;

    private String suporte;
    private String admin;
    private long cliente;
    private String agente;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        suporte = token(usuario(Perfil.SUPORTE), "senha123");
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Acao " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        cliente = ((Number) JsonPath.read(resposta, "$.cliente.id")).longValue();
        agente = "Agente " + JsonPath.read(resposta, "$.token");
        enviarAmbiente("2.4.0");
    }

    @Test
    void fluxoCompletoDoStartComAuditoria(CapturedOutput output) throws Exception {
        long pedido = pedirAcao(PARADO, "START");
        pedirAcaoResposta(PARADO, "START").andExpect(jsonPath("$.pedidoId").value(pedido));
        consultarAcao(PARADO, pedido)
                .andExpect(jsonPath("$.tipo").value("START"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        poll("2.4.0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value(pedido))
                .andExpect(jsonPath("$.tipo").value("START"))
                .andExpect(jsonPath("$.container").value(PARADO))
                .andExpect(jsonPath("$.linhas").doesNotExist());
        consultarAcao(PARADO, pedido).andExpect(jsonPath("$.status").value("ENTREGUE"));

        responder(pedido, "{\"ok\":true}").andExpect(status().isNoContent());
        consultarAcao(PARADO, pedido)
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.erro").doesNotExist())
                .andExpect(jsonPath("$.recebidoEm").isNotEmpty());
        responder(pedido, "{\"ok\":false}").andExpect(status().isConflict());

        tx.executeWithoutResult(s -> {
            PedidoConsulta p = pedidoRepo.findById(pedido).orElseThrow();
            assertThat(p.getSolicitadoPor().getLogin()).isEqualTo("acao.suporte");
            assertThat(p.getTipo()).isEqualTo(TipoPedido.START);
            assertThat(p.getContainer()).isEqualTo(PARADO);
            assertThat(p.getCriadoEm()).isNotNull();
        });
        assertThat(output.getAll())
                .contains("Ação START no container " + PARADO + " do cliente monitorado " + cliente)
                .contains("pedida por acao.suporte (pedido " + pedido + ")")
                .contains("(pedido " + pedido + ", pedida por acao.suporte): ok");
    }

    @Test
    void restartComErroDoAgente(CapturedOutput output) throws Exception {
        long pedido = pedirAcao(RODANDO, "RESTART");
        poll("2.4.1").andExpect(jsonPath("$.tipo").value("RESTART"));

        responder(pedido, "{\"ok\":false,\"erro\":\"permission denied\"}").andExpect(status().isNoContent());
        consultarAcao(RODANDO, pedido)
                .andExpect(jsonPath("$.status").value("ERRO"))
                .andExpect(jsonPath("$.erro").value("permission denied"));
        assertThat(output.getAll()).contains("(pedido " + pedido + ", pedida por acao.suporte): ERRO: permission denied");

        long semMotivo = pedirAcao(PARADO, "START");
        responder(semMotivo, "{\"ok\":false}").andExpect(status().isNoContent());
        consultarAcao(PARADO, semMotivo).andExpect(jsonPath("$.erro").value("O agente não informou o motivo"));
        responder(pedirAcao(PARADO, "START"), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void acaoQueNaoCombinaComOEstadoDoContainerResponde409() throws Exception {
        pedirAcaoResposta(RODANDO, "START")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("já está rodando")));
        pedirAcaoResposta(PARADO, "RESTART")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("está parado")));
    }

    @Test
    void agenteAnteriorA240Responde409() throws Exception {
        enviarAmbiente("2.3.0");
        pedirAcaoResposta(PARADO, "START")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("2.4.0")));
    }

    @Test
    void clienteOfflineResponde409EContainerInexistente404() throws Exception {
        pedirAcaoResposta("nao-existe", "START").andExpect(status().isNotFound());

        ClienteMonitorado c = clienteRepo.findById(cliente).orElseThrow();
        c.setUltimoContato(Instant.now().minus(Duration.ofHours(3)));
        clienteRepo.save(c);
        pedirAcaoResposta(PARADO, "START")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("offline")));
    }

    @Test
    void tipoInvalidoResponde400() throws Exception {
        pedirAcaoResposta(PARADO, "LOG").andExpect(status().isBadRequest());
        pedirAcaoResposta(PARADO, "STOP").andExpect(status().isBadRequest());
    }

    @Test
    void agenteAntigoNuncaRecebeStartOuRestart() throws Exception {
        long pedido = pedirAcao(PARADO, "START");

        poll("2.3.0").andExpect(status().isNoContent());
        poll("2.2.0").andExpect(status().isNoContent());
        consultarAcao(PARADO, pedido).andExpect(jsonPath("$.status").value("PENDENTE"));

        poll("2.4.0").andExpect(status().isOk()).andExpect(jsonPath("$.pedidoId").value(pedido));
    }

    @Test
    void agenteNaoRespondeLogComoAcaoNemAcaoComoLog() throws Exception {
        long acao = pedirAcao(PARADO, "START");
        mockMvc.perform(post("/api/monitor/agente/pedidos/{id}/log", acao)
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"x\"}"))
                .andExpect(status().isBadRequest());

        String log = mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/log", cliente, RODANDO)
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        responder(((Number) JsonPath.read(log, "$.pedidoId")).longValue(), "{\"ok\":true}")
                .andExpect(status().isBadRequest());
        // E o GET de ação não mostra o pedido de log
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao/{p}", cliente, RODANDO,
                        ((Number) JsonPath.read(log, "$.pedidoId")).longValue())
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isNotFound());
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        long pedido = pedirAcao(PARADO, "START");

        String diretoria = token(usuario(Perfil.DIRETORIA), "senha123");
        mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao/{p}", cliente, PARADO, pedido)
                        .header(HttpHeaders.AUTHORIZATION, diretoria))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao", cliente, RODANDO)
                        .param("tipo", "RESTART")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isAccepted());

        for (Perfil perfil : new Perfil[]{Perfil.COMERCIAL, Perfil.TECNICO, Perfil.PROJETOS}) {
            String t = token(usuario(perfil), "senha123");
            mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao", cliente, PARADO)
                            .param("tipo", "START")
                            .header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao/{p}", cliente, PARADO, pedido)
                            .header(HttpHeaders.AUTHORIZATION, t))
                    .andExpect(status().isForbidden());
        }
    }

    // ---- Helpers ----

    private void enviarAmbiente(String versaoAgente) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/ambiente")
                        .header(HttpHeaders.AUTHORIZATION, agente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coletadoEm":"2026-11-06T08:00:00-03:00","versaoAgente":"%s","dockerDisponivel":true,
                                 "containers":[{"nome":"%s","versao":"4.2.10","rodando":true},
                                               {"nome":"%s","versao":"4.2.10","rodando":false}]}
                                """.formatted(versaoAgente, RODANDO, PARADO)))
                .andExpect(status().isNoContent());
    }

    private ResultActions pedirAcaoResposta(String container, String tipo) throws Exception {
        return mockMvc.perform(post("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao", cliente, container)
                .param("tipo", tipo)
                .header(HttpHeaders.AUTHORIZATION, suporte));
    }

    private long pedirAcao(String container, String tipo) throws Exception {
        String resposta = pedirAcaoResposta(container, tipo)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.pedidoId")).longValue();
    }

    private ResultActions consultarAcao(String container, long pedido) throws Exception {
        return mockMvc.perform(get("/api/monitor/suporte/clientes/{id}/containers/{nome}/acao/{p}", cliente, container, pedido)
                        .header(HttpHeaders.AUTHORIZATION, suporte))
                .andExpect(status().isOk());
    }

    private ResultActions poll(String versao) throws Exception {
        return mockMvc.perform(get("/api/monitor/agente/pedidos")
                .header(HttpHeaders.AUTHORIZATION, agente)
                .header("X-Agente-Versao", versao));
    }

    private ResultActions responder(long pedido, String corpo) throws Exception {
        return mockMvc.perform(post("/api/monitor/agente/pedidos/{id}/acao", pedido)
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
        String login = "acao." + perfil.name().toLowerCase();
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
