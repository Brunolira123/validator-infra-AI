package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.ArmazenamentoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CampoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CpuExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.MemoriaExtraida;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.SistemaOperacionalExtraido;
import br.com.vrinteriorpaulista.validator_infra.service.vision.VisionProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de status do levantamento pela API, com H2 (profile "test"). O VisionProvider é mockado:
 * nenhum teste chama a IA paga, e dá para provar que ela não é chamada em levantamento bloqueado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LevantamentoStatusIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisionProvider visionProvider;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"senha\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(resposta, "$.token");
    }

    @Test
    void naoDeveChamarIaAoAnalisarEquipamentoDeLevantamentoCancelado() throws Exception {
        long levantamentoId = criarLevantamentoComEquipamentos();
        long equipamentoId = primeiroEquipamento(levantamentoId);
        acao(levantamentoId, "cancelar").andExpect(status().isOk());

        mockMvc.perform(post("/api/equipamentos/{id}/analisar", equipamentoId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Levantamento não está em análise (status atual: CANCELADO)"));

        verifyNoInteractions(visionProvider);
    }

    @Test
    void deveRetornar409AoEnviarFotoParaLevantamentoCancelado() throws Exception {
        long levantamentoId = criarLevantamentoComEquipamentos();
        long equipamentoId = primeiroEquipamento(levantamentoId);
        acao(levantamentoId, "cancelar").andExpect(status().isOk());

        mockMvc.perform(multipart("/api/equipamentos/{id}/fotos", equipamentoId)
                        .file(foto())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Levantamento não está em análise (status atual: CANCELADO)"));
    }

    @Test
    void deveRetornar409AoRevisarAnaliseDeLevantamentoCancelado() throws Exception {
        long levantamentoId = criarLevantamentoComEquipamentos();
        long equipamentoId = primeiroEquipamento(levantamentoId);
        acao(levantamentoId, "cancelar").andExpect(status().isOk());

        mockMvc.perform(put("/api/equipamentos/{id}/analise/revisar", equipamentoId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ramGb\":32}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Levantamento não está em análise (status atual: CANCELADO)"));
    }

    @Test
    void deveRetornar409AoGerarEquipamentosDuasVezes() throws Exception {
        long levantamentoId = criarLevantamentoComEquipamentos();

        acao(levantamentoId, "gerar-equipamentos")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Levantamento não está em rascunho (status atual: EM_ANALISE)"));
    }

    @Test
    void deveExecutarCicloCompletoAteConcluirEReabrir() throws Exception {
        when(visionProvider.analisarImagem(any(), anyString())).thenReturn(servidorHomologado());
        long levantamentoId = criarLevantamentoComEquipamentos();
        long equipamentoId = primeiroEquipamento(levantamentoId);

        mockMvc.perform(multipart("/api/equipamentos/{id}/fotos", equipamentoId)
                        .file(foto())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequencia").value(1));

        mockMvc.perform(post("/api/equipamentos/{id}/analisar", equipamentoId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ATENDE"));
        verify(visionProvider).analisarImagem(any(), anyString());

        mockMvc.perform(put("/api/equipamentos/{id}/analise/revisar", equipamentoId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ramGb\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("NAO_ATENDE"));

        acao(levantamentoId, "concluir")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        mockMvc.perform(post("/api/equipamentos/{id}/analisar", equipamentoId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Levantamento não está em análise (status atual: CONCLUIDO)"));

        acao(levantamentoId, "reabrir")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANALISE"));
    }

    private long criarLevantamentoComEquipamentos() throws Exception {
        String cnpj = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String cliente = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Cliente Teste\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String levantamento = mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + id(cliente) + ",\"qtdServidores\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long levantamentoId = id(levantamento);
        acao(levantamentoId, "gerar-equipamentos").andExpect(status().isOk());
        return levantamentoId;
    }

    private long primeiroEquipamento(long levantamentoId) throws Exception {
        String equipamentos = mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(equipamentos, "$[0].id")).longValue();
    }

    private ResultActions acao(long levantamentoId, String acao) throws Exception {
        return mockMvc.perform(post("/api/levantamentos/{id}/" + acao, levantamentoId)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static MockMultipartFile foto() {
        return new MockMultipartFile("foto", "servidor.png", MediaType.IMAGE_PNG_VALUE, PNG);
    }

    /**
     * Servidor de aplicação que atende todos os requisitos do seed e consta como homologado (Dell PowerEdge T140).
     */
    private static AnaliseEquipamentoDTO servidorHomologado() {
        return new AnaliseEquipamentoDTO(
                campo("Dell"), campo("PowerEdge T140"),
                new CpuExtraida(campo("Intel"), campo("Xeon E-2224"), campo(10), campo(8), campo(16)),
                new MemoriaExtraida(campo(32)),
                List.of(new ArmazenamentoExtraido(campo("SSD"), campo(960))),
                new SistemaOperacionalExtraido(campo("Windows Server 2022"), campo("Standard")),
                0.95, List.of(), null, null
        );
    }

    private static <T> CampoExtraido<T> campo(T valor) {
        return new CampoExtraido<>(valor, 0.95);
    }
}
