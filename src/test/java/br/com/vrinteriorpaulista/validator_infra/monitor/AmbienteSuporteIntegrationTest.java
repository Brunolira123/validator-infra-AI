package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.AmbienteClienteRepository;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ambiente dos clientes (containers) enviado pelo agente e a lista da tela de Suporte.
 * Cada teste cria clientes com um prefixo único e filtra a lista por ele (busca), para não ver os de outros testes.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AmbienteSuporteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClienteMonitoradoRepository clienteRepo;

    @Autowired
    private AmbienteClienteRepository ambienteRepo;

    @MockitoBean
    private DiscordNotifier discord;

    private String admin;
    private String prefixo;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        prefixo = "Suporte " + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- Ingestão ----

    @Test
    void ambienteCriaESubstituiAFotoERegistraContato() throws Exception {
        Cliente c = cadastrar(prefixo + " Mercado Á");

        enviarAmbiente(c, """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","versaoAgente":"2.2.0","dockerDisponivel":true,
                 "containers":[
                   {"nome":"vr-pdv","versao":"4.2.10","rodando":true},
                   {"nome":"vr-retaguarda","versao":"4.2.10","rodando":false},
                   {"nome":"agente","versao":"2.2.0","rodando":true}]}
                """).andExpect(status().isNoContent());

        listar("busca", prefixo)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].situacao").value("ONLINE"))
                .andExpect(jsonPath("$[0].versaoAgente").value("2.2.0"))
                .andExpect(jsonPath("$[0].ultimoContato").isNotEmpty())
                .andExpect(jsonPath("$[0].ambiente.coletadoEm").value("2026-11-06T11:00:00Z"))
                .andExpect(jsonPath("$[0].ambiente.dockerDisponivel").value(true))
                .andExpect(jsonPath("$[0].ambiente.total").value(3))
                .andExpect(jsonPath("$[0].ambiente.rodando").value(2))
                .andExpect(jsonPath("$[0].ambiente.parados").value(1))
                // Parados primeiro, depois por nome
                .andExpect(jsonPath("$[0].ambiente.containers[*].nome").value(contains("vr-retaguarda", "agente", "vr-pdv")))
                .andExpect(jsonPath("$[0].ambiente.containers[0].versao").value("4.2.10"))
                .andExpect(jsonPath("$[0].ambiente.containers[0].rodando").value(false));

        enviarAmbiente(c, """
                {"coletadoEm":"2026-11-06T09:00:00-03:00","versaoAgente":"2.2.1","dockerDisponivel":false,
                 "erro":"permission denied on /var/run/docker.sock","containers":[]}
                """).andExpect(status().isNoContent());

        listar("busca", prefixo)
                .andExpect(jsonPath("$[0].versaoAgente").value("2.2.1"))
                .andExpect(jsonPath("$[0].ambiente.coletadoEm").value("2026-11-06T12:00:00Z"))
                .andExpect(jsonPath("$[0].ambiente.dockerDisponivel").value(false))
                .andExpect(jsonPath("$[0].ambiente.erro").value("permission denied on /var/run/docker.sock"))
                .andExpect(jsonPath("$[0].ambiente.total").value(0))
                .andExpect(jsonPath("$[0].ambiente.containers", hasSize(0)));
        assertThat(ambienteRepo.findById(c.id())).isPresent();
    }

    @Test
    void ambienteComTokenInvalidoOuSemTokenResponde401() throws Exception {
        String corpo = """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","dockerDisponivel":true,"containers":[]}
                """;
        mockMvc.perform(post("/api/monitor/agente/ambiente")
                        .header(HttpHeaders.AUTHORIZATION, "Agente vrip_invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/monitor/agente/ambiente")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ambienteRecusaMaisDe100ContainersENomeGrande() throws Exception {
        Cliente c = cadastrar(prefixo + " Limites");
        String muitos = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "{\"nome\":\"c" + i + "\",\"rodando\":true}")
                .collect(Collectors.joining(","));
        enviarAmbiente(c, """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","dockerDisponivel":true,"containers":[%s]}
                """.formatted(muitos)).andExpect(status().isBadRequest());
        enviarAmbiente(c, """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","dockerDisponivel":true,
                 "containers":[{"nome":"%s","rodando":true}]}
                """.formatted("x".repeat(101))).andExpect(status().isBadRequest());

        assertThat(ambienteRepo.findById(c.id())).isEmpty();
    }

    @Test
    void ambienteRecusadoLogaClienteEMotivoSemMudarAResposta(CapturedOutput output) throws Exception {
        Cliente c = cadastrar(prefixo + " Recusado");

        enviarAmbiente(c, """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","containers":[{"nome":"","rodando":true}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
                .andExpect(jsonPath("$.path").value("/api/monitor/agente/ambiente"))
                .andExpect(jsonPath("$.campos[*].campo")
                        .value(containsInAnyOrder("dockerDisponivel", "containers[0].nome")));

        assertThat(output.getAll())
                .containsPattern("WARN.*Ambiente recusado do agente cliente " + c.id() + " \\(" + prefixo + " Recusado\\)")
                .contains("dockerDisponivel (")
                .contains("containers[0].nome (");

        enviarAmbiente(c, "{\"coletadoEm\":\"ontem\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisição inválido ou mal formatado"));
        assertThat(output.getAll()).containsPattern(
                "WARN.*Ambiente recusado do agente cliente " + c.id() + " .*: corpo inválido: ");
    }

    @Test
    void recusaDeOutroEndpointDoAgenteNaoLogaComoAmbiente(CapturedOutput output) throws Exception {
        Cliente c = cadastrar(prefixo + " Resultado");
        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lojas\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos"));

        assertThat(output.getAll()).doesNotContain("Ambiente recusado do agente cliente " + c.id() + " ");
    }

    // ---- Lista do Suporte ----

    @Test
    void listaClientesComESemAmbiente() throws Exception {
        Cliente semAmbiente = cadastrar(prefixo + " A Sem ambiente");
        Cliente comAmbiente = cadastrar(prefixo + " B Com ambiente");
        enviarResultado(semAmbiente, "4.2.9");
        enviarAmbiente(comAmbiente, ambienteSimples(true)).andExpect(status().isNoContent());

        Cliente inativo = cadastrar(prefixo + " C Inativo");
        ClienteMonitorado i = clienteRepo.findById(inativo.id()).orElseThrow();
        i.setAtivo(false);
        clienteRepo.save(i);

        listar("busca", prefixo)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(semAmbiente.id()))
                .andExpect(jsonPath("$[0].versaoBanco").value("4.2.9"))
                .andExpect(jsonPath("$[0].situacao").value("ONLINE"))
                .andExpect(jsonPath("$[0].ambiente").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(comAmbiente.id()))
                .andExpect(jsonPath("$[1].versaoBanco").doesNotExist())
                .andExpect(jsonPath("$[1].ambiente.total").value(1));
    }

    @Test
    void filtrosPorNomeContainerParadoEOffline() throws Exception {
        Cliente parado = cadastrar(prefixo + " São João Parado");
        Cliente rodando = cadastrar(prefixo + " Rodando");
        Cliente offline = cadastrar(prefixo + " Offline");
        Cliente nuncaFalou = cadastrar(prefixo + " Nunca falou");
        enviarAmbiente(parado, ambienteSimples(false)).andExpect(status().isNoContent());
        enviarAmbiente(rodando, ambienteSimples(true)).andExpect(status().isNoContent());
        enviarAmbiente(offline, ambienteSimples(false)).andExpect(status().isNoContent());
        ClienteMonitorado o = clienteRepo.findById(offline.id()).orElseThrow();
        o.setUltimoContato(Instant.now().minus(Duration.ofHours(3)));
        clienteRepo.save(o);

        // Busca ignora acento e maiúscula
        listar("busca", prefixo.toUpperCase() + " sao joao")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(parado.id()));

        listar("busca", prefixo, "comParado", "true")
                .andExpect(jsonPath("$[*].id").value(contains(offline.id().intValue(), parado.id().intValue())));

        listar("busca", prefixo, "offline", "true")
                .andExpect(jsonPath("$[*].id").value(contains(nuncaFalou.id().intValue(), offline.id().intValue())))
                .andExpect(jsonPath("$[0].ambiente").doesNotExist())
                .andExpect(jsonPath("$[1].situacao").value("OFFLINE"));

        listar("busca", prefixo, "offline", "true", "comParado", "true")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(offline.id()));
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        for (Perfil perfil : new Perfil[]{Perfil.SUPORTE, Perfil.DIRETORIA, Perfil.ADMIN}) {
            mockMvc.perform(get("/api/monitor/suporte/clientes")
                            .header(HttpHeaders.AUTHORIZATION, token(usuario(perfil), "senha123")))
                    .andExpect(status().isOk());
        }
        for (Perfil perfil : new Perfil[]{Perfil.COMERCIAL, Perfil.TECNICO, Perfil.PROJETOS}) {
            mockMvc.perform(get("/api/monitor/suporte/clientes")
                            .header(HttpHeaders.AUTHORIZATION, token(usuario(perfil), "senha123")))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/monitor/suporte/clientes")).andExpect(status().isUnauthorized());
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

    private ResultActions enviarAmbiente(Cliente c, String corpo) throws Exception {
        return mockMvc.perform(post("/api/monitor/agente/ambiente")
                .header(HttpHeaders.AUTHORIZATION, c.agente())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private static String ambienteSimples(boolean rodando) {
        return """
                {"coletadoEm":"2026-11-06T08:00:00-03:00","versaoAgente":"2.2.0","dockerDisponivel":true,
                 "containers":[{"nome":"vr-pdv","versao":"4.2.10","rodando":%s}]}
                """.formatted(rodando);
    }

    private void enviarResultado(Cliente c, String versaoBanco) throws Exception {
        mockMvc.perform(post("/api/monitor/agente/resultados")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origem":"SOB_DEMANDA","executadoEm":"2026-11-06T07:00:00-03:00",
                                 "versaoAgente":"2.2.0","versaoBanco":"%s","lojas":[]}
                                """.formatted(versaoBanco)))
                .andExpect(status().isNoContent());
    }

    /** Pares nome/valor de parâmetros. */
    private ResultActions listar(String... params) throws Exception {
        var req = get("/api/monitor/suporte/clientes").header(HttpHeaders.AUTHORIZATION, admin);
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
        String login = "suporte." + perfil.name().toLowerCase();
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
