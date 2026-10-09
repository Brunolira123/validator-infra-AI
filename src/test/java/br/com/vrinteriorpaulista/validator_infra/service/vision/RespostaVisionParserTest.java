package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RespostaVisionParserTest {

    private static final String JSON = """
            {
              "fabricante": {"valor": "Dell", "confianca": 0.95},
              "modelo": {"valor": "OptiPlex 7090", "confianca": 0.9},
              "cpu": {"fabricante": {"valor": "Intel", "confianca": 0.9}, "geracao": {"valor": 10, "confianca": 0.9}},
              "memoria": {"total_gb": {"valor": 16, "confianca": 0.9}},
              "armazenamento": [{"tipo": {"valor": "SSD", "confianca": 0.9}, "capacidade_gb": {"valor": 512, "confianca": 0.9}}],
              "confianca_global": 0.9,
              "campos_inconclusivos": [],
              "observacoes": "Etiqueta legível"
            }""";

    private final RespostaVisionParser parser = new RespostaVisionParser(JsonMapper.builder().build());

    @Nested
    class ComJson {

        @Test
        void deveDesserializarJsonPuro() {
            assertDadosDoDell(parser.interpretar(JSON));
        }

        @Test
        void deveDesserializarJsonEmCercaMarkdown() {
            assertDadosDoDell(parser.interpretar("```json\n" + JSON + "\n```"));
        }

        @Test
        void deveDesserializarJsonComPreambulo() {
            assertDadosDoDell(parser.interpretar("Claro! Aqui está a análise do equipamento:\n\n" + JSON));
        }

        @Test
        void deveDesserializarJsonNoMeioDoRaciocinio() {
            String resposta = """
                    Vou analisar a imagem. Vejo uma etiqueta da Dell, modelo OptiPlex 7090.
                    A memória parece ser de 16 GB. Montando a resposta:
                    """ + JSON + """

                    Observação final: a confiança é alta porque a etiqueta está nítida.
                    """;
            assertDadosDoDell(parser.interpretar(resposta));
        }

        @Test
        void deveIgnorarChavesNoRaciocinioAntesDoJson() {
            String resposta = "O schema pede cada campo como {valor, confianca} e listas como [..]. "
                    + "Para a CPU uso {fabricante, modelo}. Resposta:\n" + JSON;
            assertDadosDoDell(parser.interpretar(resposta));
        }

        @Test
        void deveIgnorarExemploDeJsonForaDoSchemaNoRaciocinio() {
            String resposta = "Campos ilegíveis devem ser {\"valor\": null, \"confianca\": 0.0}. Agora a resposta:\n" + JSON;
            assertDadosDoDell(parser.interpretar(resposta));
        }

        @Test
        void deveAceitarChavesDentroDeStrings() {
            String json = JSON.replace("\"Etiqueta legível\"", "\"Formato {x} citado na etiqueta \\\"}\\\"\"");
            AnaliseEquipamentoDTO dto = parser.interpretar("Resposta: " + json);
            assertDadosDoDell(dto);
            assertThat(dto.observacoes()).isEqualTo("Formato {x} citado na etiqueta \"}\"");
        }

        @Test
        void deveManterErroInformadoPelaIa() {
            AnaliseEquipamentoDTO dto = parser.interpretar("Não é um computador. {\"erro\": \"imagem_nao_reconhecida\"}");
            assertThat(dto.erro()).isEqualTo("imagem_nao_reconhecida");
        }
    }

    @Nested
    class SemJsonUtilizavel {

        @Test
        void deveRetornarErroQuandoNaoHouverJson() {
            AnaliseEquipamentoDTO dto = parser.interpretar("User Safety: safe");

            assertThat(dto.erro()).isEqualTo("User Safety: safe");
            assertThat(dto.fabricante()).isNull();
            assertThat(dto.campos_inconclusivos()).isEmpty();
        }

        @Test
        void deveRetornarErroQuandoRespostaForVazia() {
            assertThat(parser.interpretar("   ").erro()).isEqualTo("Resposta vazia da IA");
            assertThat(parser.interpretar(null).erro()).isEqualTo("Resposta vazia da IA");
        }

        @Test
        void deveRetornarErroQuandoJsonEstiverTruncado() {
            String truncado = "Pensando...\n" + JSON.substring(0, JSON.length() / 2);

            AnaliseEquipamentoDTO dto = parser.interpretar(truncado);

            assertThat(dto.erro()).startsWith("Pensando...");
            assertThat(dto.fabricante()).as("não pode aceitar o objeto interno da CPU como resposta").isNull();
        }

        @Test
        void deveRetornarErroQuandoTiposNaoBaterem() {
            String json = JSON.replace("{\"valor\": 16, ", "{\"valor\": \"dezesseis GB\", ");

            assertThat(parser.interpretar(json).erro()).isNotNull();
        }

        @Test
        void deveLimitarErroA200CaracteresEColapsarEspacos() {
            String longo = "Raciocinio   longo\n\n" + "x".repeat(500);

            String erro = parser.interpretar(longo).erro();

            assertThat(erro).hasSize(RespostaVisionParser.TAMANHO_TRECHO_ERRO);
            assertThat(erro).startsWith("Raciocinio longo x");
        }
    }

    private static void assertDadosDoDell(AnaliseEquipamentoDTO dto) {
        assertThat(dto.erro()).isNull();
        assertThat(dto.fabricante().valor()).isEqualTo("Dell");
        assertThat(dto.modelo().valor()).isEqualTo("OptiPlex 7090");
        assertThat(dto.cpu().geracao().valor()).isEqualTo(10);
        assertThat(dto.memoria().total_gb().valor()).isEqualTo(16);
        assertThat(dto.armazenamento()).hasSize(1);
        assertThat(dto.armazenamento().get(0).capacidade_gb().valor()).isEqualTo(512);
    }
}
