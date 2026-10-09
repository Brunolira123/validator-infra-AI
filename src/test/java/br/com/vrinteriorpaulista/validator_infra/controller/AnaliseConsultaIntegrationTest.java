package br.com.vrinteriorpaulista.validator_infra.controller;

import br.com.vrinteriorpaulista.validator_infra.config.IntegrationTest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta da análise persistida (GET /api/equipamentos/{id}/analise), contra Postgres no Testcontainers.
 * O VisionProvider é mockado para não chamar a IA paga.
 */
@IntegrationTest
class AnaliseConsultaIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisionProvider visionProvider;

    private String token;
    private long equipamento;

    @BeforeEach
    void setUp() throws Exception {
        String resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"senha\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(resposta, "$.token");
        equipamento = criarEquipamento();
    }

    @Test
    void deveRetornarAAnalisePersistidaComOsItens() throws Exception {
        when(visionProvider.analisarImagem(any(), anyString())).thenReturn(servidorHomologado());
        enviarFotoEAnalisar();

        mockMvc.perform(get("/api/equipamentos/{id}/analise", equipamento).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ATENDE"))
                .andExpect(jsonPath("$.justificativa").exists())
                .andExpect(jsonPath("$.itens", not(hasSize(0))))
                .andExpect(jsonPath("$.itens[0].campo").exists())
                .andExpect(jsonPath("$.itens[0].status").exists())
                .andExpect(jsonPath("$.fabricante").value("Dell"))
                .andExpect(jsonPath("$.modelo").value("PowerEdge T140"))
                .andExpect(jsonPath("$.cpuFabricante").value("Intel"))
                .andExpect(jsonPath("$.cpuModelo").value("Xeon E-2224"))
                .andExpect(jsonPath("$.cpuGeracao").value(10))
                .andExpect(jsonPath("$.cpuCores").value(8))
                .andExpect(jsonPath("$.cpuThreads").value(16))
                .andExpect(jsonPath("$.ramGb").value(32))
                .andExpect(jsonPath("$.armazenamentoTipo").value("SSD"))
                .andExpect(jsonPath("$.armazenamentoGb").value(960))
                .andExpect(jsonPath("$.soNome").value("Windows Server 2022"))
                .andExpect(jsonPath("$.soVersao").value("Standard"))
                .andExpect(jsonPath("$.confiancaGlobal").value(0.95))
                .andExpect(jsonPath("$.analisadoEm").exists())
                .andExpect(jsonPath("$.analisadoPorNome").exists())
                .andExpect(jsonPath("$.revisada").value(false));
    }

    @Test
    void deveRefletirARevisaoDoTecnico() throws Exception {
        when(visionProvider.analisarImagem(any(), anyString())).thenReturn(servidorHomologado());
        enviarFotoEAnalisar();

        mockMvc.perform(put("/api/equipamentos/{id}/analise/revisar", equipamento)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ramGb\":8,\"cpuCores\":4,\"armazenamentoTipo\":\"HDD\",\"soVersao\":\"Datacenter\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/equipamentos/{id}/analise", equipamento).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("NAO_ATENDE"))
                .andExpect(jsonPath("$.ramGb").value(8))
                .andExpect(jsonPath("$.cpuCores").value(4))
                .andExpect(jsonPath("$.armazenamentoTipo").value("HDD"))
                .andExpect(jsonPath("$.soVersao").value("Datacenter"))
                // campos não enviados na revisão mantêm o que a IA leu
                .andExpect(jsonPath("$.fabricante").value("Dell"))
                .andExpect(jsonPath("$.cpuFabricante").value("Intel"))
                .andExpect(jsonPath("$.cpuThreads").value(16))
                .andExpect(jsonPath("$.armazenamentoGb").value(960))
                .andExpect(jsonPath("$.soNome").value("Windows Server 2022"))
                .andExpect(jsonPath("$.itens", not(hasSize(0))))
                .andExpect(jsonPath("$.revisada").value(true));
    }

    @Test
    void deveRetornar204QuandoEquipamentoNaoFoiAnalisado() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/analise", equipamento).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void deveRetornar404QuandoEquipamentoNaoExistir() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/analise", 999_999).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/api/equipamentos/{id}/analise", equipamento))
                .andExpect(status().isUnauthorized());
    }

    private void enviarFotoEAnalisar() throws Exception {
        mockMvc.perform(multipart("/api/equipamentos/{id}/fotos", equipamento)
                        .file(new MockMultipartFile("foto", "servidor.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/equipamentos/{id}/analisar", equipamento).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    private long criarEquipamento() throws Exception {
        String cnpj = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        String cliente = mockMvc.perform(post("/api/clientes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + cnpj + "\",\"razaoSocial\":\"Cliente Análise\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String levantamento = mockMvc.perform(post("/api/levantamentos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":" + id(cliente) + ",\"qtdServidores\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long levantamentoId = id(levantamento);

        mockMvc.perform(post("/api/levantamentos/{id}/gerar-equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        String equipamentos = mockMvc.perform(get("/api/levantamentos/{id}/equipamentos", levantamentoId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(equipamentos, "$[0].id")).longValue();
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
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
