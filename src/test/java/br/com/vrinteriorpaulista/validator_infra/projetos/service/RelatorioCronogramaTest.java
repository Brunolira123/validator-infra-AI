package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioCronogramaTest {

    private final RelatorioCronograma relatorio = new RelatorioCronograma();

    @Test
    void geraPdfComCabecalhoEtapasEAcentos() throws IOException {
        byte[] pdf = relatorio.gerar(
                new RelatorioCronograma.Cabecalho("Mercado São João", "Implantação loja 2", "23/11/2026", "04/12/2026",
                        10, "10/11/2026", true),
                List.of(new RelatorioCronograma.Linha("Conversão de banco", "VR", "23/11/2026", "24/11/2026", false),
                        new RelatorioCronograma.Linha("Conferência dos dados", "Mercado São João", "25/11/2026",
                                "26/11/2026", true)));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String texto = texto(pdf);
        assertThat(texto).contains("VRIP", "Cronograma de implantação", "Mercado São João", "Implantação loja 2",
                "23/11/2026", "04/12/2026", "Conversão de banco", "Conferência dos dados",
                "atrasos nelas deslocam as etapas seguintes", "Página 1");
    }

    @Test
    void semEtapaDoClienteNaoMostraANota() throws IOException {
        byte[] pdf = relatorio.gerar(
                new RelatorioCronograma.Cabecalho("Mercado", "Projeto", "23/11/2026", "23/11/2026", 1, "10/11/2026", false),
                List.of(new RelatorioCronograma.Linha("Kickoff", "VR", "23/11/2026", "23/11/2026", false)));

        assertThat(texto(pdf)).contains("Kickoff").doesNotContain("deslocam");
    }

    static String texto(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        try {
            PdfTextExtractor extrator = new PdfTextExtractor(leitor);
            StringBuilder sb = new StringBuilder();
            for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
                sb.append(extrator.getTextFromPage(pagina)).append('\n');
            }
            return sb.toString();
        } finally {
            leitor.close();
        }
    }
}
