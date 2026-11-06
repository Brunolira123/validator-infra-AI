package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.enums.Perfil;
import br.com.vrinteriorpaulista.validator_infra.monitor.entity.ClienteMonitorado;
import br.com.vrinteriorpaulista.validator_infra.monitor.repository.ClienteMonitoradoRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Atualização do agente pelo portal: vrip-updater (token do agente) e versão alvo/piloto na tela de Suporte.
 */
@IntegrationTest
class AtualizacaoAgenteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClienteMonitoradoRepository clienteRepo;

    @MockitoBean
    private DiscordNotifier discord;

    private String admin;
    private String prefixo;

    @BeforeEach
    void setUp() throws Exception {
        admin = token("admin", "admin");
        prefixo = "Atualiza " + UUID.randomUUID().toString().substring(0, 8);
        // O escopo PILOTO vale para o banco todo: começa sem piloto de outros testes
        for (ClienteMonitorado c : clienteRepo.findByAtivoTrueAndPilotoTrue()) {
            c.setPiloto(false);
            clienteRepo.save(c);
        }
    }

    // ---- vrip-updater ----

    @Test
    void semAlvoResponde204ERegistraOContatoDoUpdater() throws Exception {
        Cliente c = cadastrar("A");

        consultarAtualizacao(c).andExpect(status().isNoContent());

        item(c)
                .andExpect(jsonPath("$[0].updater.versao").value("1.0.0"))
                .andExpect(jsonPath("$[0].updater.ultimoContato").isNotEmpty())
                .andExpect(jsonPath("$[0].versaoAlvoAgente").doesNotExist())
                .andExpect(jsonPath("$[0].piloto").value(false))
                .andExpect(jsonPath("$[0].ultimaAtualizacao").doesNotExist())
                // O updater não conta como contato do agente
                .andExpect(jsonPath("$[0].ultimoContato").doesNotExist())
                .andExpect(jsonPath("$[0].situacao").value("OFFLINE"));
    }

    @Test
    void updaterNuncaConsultouVemNuloETokenInvalido401() throws Exception {
        Cliente c = cadastrar("A");
        item(c).andExpect(jsonPath("$[0].updater").doesNotExist());

        mockMvc.perform(get("/api/monitor/agente/atualizacao")
                        .header(HttpHeaders.AUTHORIZATION, "Agente vrip_invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/monitor/agente/atualizacao/resultado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versaoAlvo\":\"2.5.0\",\"status\":\"ATUALIZADO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void atualizadoLimpaOAlvoEGravaHistorico() throws Exception {
        Cliente c = cadastrar("A");
        definirAlvo(c, "\"2.5.0\"").andExpect(status().isNoContent());

        consultarAtualizacao(c)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoAlvo").value("2.5.0"));

        enviarResultado(c, "2.4.0", "2.5.0", "ATUALIZADO", null).andExpect(status().isNoContent());

        consultarAtualizacao(c).andExpect(status().isNoContent());
        item(c)
                .andExpect(jsonPath("$[0].versaoAlvoAgente").doesNotExist())
                .andExpect(jsonPath("$[0].ultimaAtualizacao.status").value("ATUALIZADO"))
                .andExpect(jsonPath("$[0].ultimaAtualizacao.versaoAnterior").value("2.4.0"))
                .andExpect(jsonPath("$[0].ultimaAtualizacao.versaoAlvo").value("2.5.0"))
                .andExpect(jsonPath("$[0].ultimaAtualizacao.em").isNotEmpty());
    }

    @Test
    void falhaNaoRepeteAMesmaVersaoSoUmaNova() throws Exception {
        Cliente c = cadastrar("A");
        definirAlvo(c, "\"2.5.0\"");
        consultarAtualizacao(c).andExpect(status().isOk());

        enviarResultado(c, "2.4.0", "2.5.0", "FALHOU", "\"download falhou\"").andExpect(status().isNoContent());

        // Alvo continua, mas o updater não tenta a mesma versão de novo
        consultarAtualizacao(c).andExpect(status().isNoContent());
        item(c)
                .andExpect(jsonPath("$[0].versaoAlvoAgente").value("2.5.0"))
                .andExpect(jsonPath("$[0].ultimaAtualizacao.status").value("FALHOU"))
                .andExpect(jsonPath("$[0].ultimaAtualizacao.detalhe").value("download falhou"));
        // Marcar de novo a mesma versão também não faz tentar
        definirAlvo(c, "\"2.5.0\"");
        consultarAtualizacao(c).andExpect(status().isNoContent());

        definirAlvo(c, "\"2.5.1\"");
        consultarAtualizacao(c).andExpect(status().isOk()).andExpect(jsonPath("$.versaoAlvo").value("2.5.1"));

        enviarResultado(c, "2.4.0", "2.5.1", "ROLLBACK", "\"healthcheck falhou\"").andExpect(status().isNoContent());
        consultarAtualizacao(c).andExpect(status().isNoContent());
        item(c).andExpect(jsonPath("$[0].ultimaAtualizacao.status").value("ROLLBACK"));
    }

    @Test
    void agenteJaNaVersaoAlvoNaoRecebeEAlvoNuloCancela() throws Exception {
        Cliente c = cadastrar("A");
        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .header("X-Agente-Versao", "2.5.0"))
                .andExpect(status().isNoContent());

        definirAlvo(c, "\"2.5.0\"");
        consultarAtualizacao(c).andExpect(status().isNoContent());

        definirAlvo(c, "\"2.6.0\"");
        consultarAtualizacao(c).andExpect(status().isOk());
        definirAlvo(c, "null").andExpect(status().isNoContent());
        consultarAtualizacao(c).andExpect(status().isNoContent());
        item(c).andExpect(jsonPath("$[0].versaoAlvoAgente").doesNotExist());
    }

    @Test
    void resultadoInvalidoResponde400() throws Exception {
        Cliente c = cadastrar("A");
        enviarResultado(c, "2.4.0", "2.5.0", "TALVEZ", null).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/monitor/agente/atualizacao/resultado")
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ATUALIZADO\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- Suporte ----

    @Test
    void versaoInvalidaResponde400() throws Exception {
        Cliente c = cadastrar("A");
        for (String versao : new String[]{"\"2.5\"", "\"v2.5.0\"", "\"2.5.0 \"", "\"2.5.0-\"", "\"2.5.0-rc_1\""}) {
            definirAlvo(c, versao).andExpect(status().isBadRequest());
        }
        definirAlvo(c, "\"2.5.0-rc.1\"").andExpect(status().isNoContent());

        lote("{\"versao\":\"abc\",\"escopo\":\"TODOS\"}").andExpect(status().isBadRequest());
        lote("{\"escopo\":\"TODOS\"}").andExpect(status().isBadRequest());
        lote("{\"versao\":\"2.6.0\"}").andExpect(status().isBadRequest());
        lote("{\"versao\":\"2.6.0\",\"escopo\":\"IDS\"}").andExpect(status().isBadRequest());
        lote("{\"versao\":\"2.6.0\",\"escopo\":\"IDS\",\"ids\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void escopoPilotoMarcaSoOsPilotosQueAindaNaoEstaoNaVersao() throws Exception {
        Cliente piloto = cadastrar("A Piloto");
        Cliente pilotoJaNaVersao = cadastrar("B Piloto na versao");
        Cliente comum = cadastrar("C Comum");
        definirPiloto(piloto, true);
        definirPiloto(pilotoJaNaVersao, true);
        definirPiloto(comum, true);
        definirPiloto(comum, false);
        mockMvc.perform(get("/api/monitor/agente/pedidos")
                        .header(HttpHeaders.AUTHORIZATION, pilotoJaNaVersao.agente())
                        .header("X-Agente-Versao", "2.6.0"))
                .andExpect(status().isNoContent());

        lote("{\"versao\":\"2.6.0\",\"escopo\":\"PILOTO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcados").value(1));

        listar()
                .andExpect(jsonPath("$[0].piloto").value(true))
                .andExpect(jsonPath("$[0].versaoAlvoAgente").value("2.6.0"))
                .andExpect(jsonPath("$[1].piloto").value(true))
                .andExpect(jsonPath("$[1].versaoAlvoAgente").doesNotExist())
                .andExpect(jsonPath("$[2].piloto").value(false))
                .andExpect(jsonPath("$[2].versaoAlvoAgente").doesNotExist());
    }

    @Test
    void escopoTodosEIds() throws Exception {
        Cliente a = cadastrar("A");
        Cliente b = cadastrar("B");
        Cliente inativo = cadastrar("C Inativo");
        ClienteMonitorado i = clienteRepo.findById(inativo.id()).orElseThrow();
        i.setAtivo(false);
        clienteRepo.save(i);

        lote("{\"versao\":\"2.7.0\",\"escopo\":\"IDS\",\"ids\":[%d,%d,999999999]}".formatted(a.id(), inativo.id()))
                .andExpect(jsonPath("$.marcados").value(1));
        listar()
                .andExpect(jsonPath("$[0].versaoAlvoAgente").value("2.7.0"))
                .andExpect(jsonPath("$[1].versaoAlvoAgente").doesNotExist());

        lote("{\"versao\":\"2.8.0\",\"escopo\":\"TODOS\"}")
                .andExpect(jsonPath("$.marcados", greaterThanOrEqualTo(2)));
        listar()
                .andExpect(jsonPath("$[0].versaoAlvoAgente").value("2.8.0"))
                .andExpect(jsonPath("$[1].versaoAlvoAgente").value("2.8.0"));
        assertThat(clienteRepo.findById(inativo.id()).orElseThrow().getVersaoAlvoAgente()).isNull();
        consultarAtualizacao(b).andExpect(jsonPath("$.versaoAlvo").value("2.8.0"));
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        Cliente c = cadastrar("A");

        String diretoria = token(usuario(Perfil.DIRETORIA), "senha123");
        definirAlvo(c, "\"2.5.0\"", diretoria).andExpect(status().isNoContent());
        definirPiloto(c, true, diretoria).andExpect(status().isNoContent());
        lote("{\"versao\":\"2.5.0\",\"escopo\":\"IDS\",\"ids\":[" + c.id() + "]}", diretoria)
                .andExpect(status().isOk());

        for (Perfil perfil : new Perfil[]{Perfil.SUPORTE, Perfil.COMERCIAL, Perfil.TECNICO}) {
            String t = token(usuario(perfil), "senha123");
            definirAlvo(c, "\"2.5.0\"", t).andExpect(status().isForbidden());
            definirPiloto(c, false, t).andExpect(status().isForbidden());
            lote("{\"versao\":\"2.5.0\",\"escopo\":\"TODOS\"}", t).andExpect(status().isForbidden());
        }
        // Suporte só vê
        mockMvc.perform(get("/api/monitor/suporte/clientes")
                        .param("busca", prefixo)
                        .header(HttpHeaders.AUTHORIZATION, token(usuario(Perfil.SUPORTE), "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].piloto").value(true));
        // Token do agente não muda a versão alvo
        mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/agente/versao-alvo", c.id())
                        .header(HttpHeaders.AUTHORIZATION, c.agente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versao\":\"9.9.9\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Helpers ----

    private record Cliente(Long id, String agente) {}

    private Cliente cadastrar(String sufixo) throws Exception {
        String resposta = mockMvc.perform(post("/api/monitor/clientes")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + prefixo + " " + sufixo + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Cliente(((Number) JsonPath.read(resposta, "$.cliente.id")).longValue(),
                "Agente " + JsonPath.read(resposta, "$.token"));
    }

    private ResultActions consultarAtualizacao(Cliente c) throws Exception {
        return mockMvc.perform(get("/api/monitor/agente/atualizacao")
                .header(HttpHeaders.AUTHORIZATION, c.agente())
                .header("X-Updater-Versao", "1.0.0"));
    }

    private ResultActions enviarResultado(Cliente c, String anterior, String alvo, String status, String detalheJson)
            throws Exception {
        return mockMvc.perform(post("/api/monitor/agente/atualizacao/resultado")
                .header(HttpHeaders.AUTHORIZATION, c.agente())
                .header("X-Updater-Versao", "1.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versaoAnterior\":\"%s\",\"versaoAlvo\":\"%s\",\"status\":\"%s\",\"detalhe\":%s}"
                        .formatted(anterior, alvo, status, detalheJson)));
    }

    private ResultActions definirAlvo(Cliente c, String versaoJson) throws Exception {
        return definirAlvo(c, versaoJson, admin);
    }

    private ResultActions definirAlvo(Cliente c, String versaoJson, String token) throws Exception {
        return mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/agente/versao-alvo", c.id())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versao\":" + versaoJson + "}"));
    }

    private void definirPiloto(Cliente c, boolean piloto) throws Exception {
        definirPiloto(c, piloto, admin).andExpect(status().isNoContent());
    }

    private ResultActions definirPiloto(Cliente c, boolean piloto, String token) throws Exception {
        return mockMvc.perform(put("/api/monitor/suporte/clientes/{id}/piloto", c.id())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"piloto\":" + piloto + "}"));
    }

    private ResultActions lote(String corpo) throws Exception {
        return lote(corpo, admin);
    }

    private ResultActions lote(String corpo, String token) throws Exception {
        return mockMvc.perform(post("/api/monitor/suporte/agente/versao-alvo")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    /** Clientes deste teste, por nome. */
    private ResultActions listar() throws Exception {
        return mockMvc.perform(get("/api/monitor/suporte/clientes")
                        .param("busca", prefixo)
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    private ResultActions item(Cliente c) throws Exception {
        return listar().andExpect(jsonPath("$[0].id").value(c.id()));
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
        String login = "atualiza." + perfil.name().toLowerCase();
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
