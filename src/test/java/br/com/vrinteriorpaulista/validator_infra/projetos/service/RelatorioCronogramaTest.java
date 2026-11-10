package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintImage;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioCronogramaTest {

    /** Área útil sobre o timbrado: abaixo da faixa do topo, acima do endereço, com 40pt nas laterais. */
    private static final int TOPO = 95;
    private static final int BASE = 842 - 90;
    private static final int ESQUERDA = 40;
    private static final int DIREITA = 595 - 40;

    private final RelatorioCronograma relatorio = new RelatorioCronograma();

    @Test
    void geraPdfComCabecalhoEtapasNotasEAcentos() throws IOException {
        byte[] pdf = relatorio.gerar(cabecalho(true), List.of(
                new RelatorioCronograma.Linha("Conversão de banco", "VR Software", "23/11/2026", "24/11/2026", false),
                new RelatorioCronograma.Linha("Conferência dos dados", "Mercado São João", "25/11/2026", "26/11/2026", true)));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(texto(pdf)).contains("Cronograma de implantação", "Mercado São João · Implantação loja 2",
                "Início", "Término previsto", "Dias úteis", "Emitido em", "10/11/2026", "Responsável",
                "Conversão de banco", "VR Software", "Conferência dos dados",
                "Etapas em negrito dependem do cliente: atrasos nelas deslocam as etapas seguintes.",
                "Datas previstas, sujeitas a ajuste", "Página 1 de 1");
    }

    @Test
    void semEtapaDoClienteNaoMostraANotaDoCliente() throws IOException {
        byte[] pdf = relatorio.gerar(cabecalho(false),
                List.of(new RelatorioCronograma.Linha("Kickoff", "VR Software", "23/11/2026", "23/11/2026", false)));

        assertThat(texto(pdf)).contains("Kickoff", "Datas previstas").doesNotContain("dependem do cliente");
    }

    @Test
    void quarentaEtapasQuebramPaginaComTimbradoEmTodasENadaForaDaAreaUtil() throws IOException {
        List<RelatorioCronograma.Linha> linhas = quarentaEtapas();

        JasperPrint impresso = relatorio.preencher(cabecalho(true), linhas);

        assertThat(impresso.getPages()).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 0; i < impresso.getPages().size(); i++) {
            JRPrintPage pagina = impresso.getPages().get(i);
            List<JRPrintElement> timbrado = new ArrayList<>();
            for (JRPrintElement e : pagina.getElements()) {
                if (e instanceof JRPrintImage) {
                    timbrado.add(e);
                    continue;
                }
                String onde = "página " + (i + 1) + ", elemento em x=" + e.getX() + " y=" + e.getY()
                        + " (" + e.getWidth() + "x" + e.getHeight() + ")";
                assertThat(e.getY()).as(onde).isGreaterThanOrEqualTo(TOPO);
                assertThat(e.getY() + e.getHeight()).as(onde).isLessThanOrEqualTo(BASE);
                assertThat(e.getX()).as(onde).isGreaterThanOrEqualTo(ESQUERDA);
                assertThat(e.getX() + e.getWidth()).as(onde).isLessThanOrEqualTo(DIREITA);
            }
            assertThat(timbrado).as("timbrado na página " + (i + 1)).singleElement().satisfies(img -> {
                assertThat(img.getX()).isZero();
                assertThat(img.getY()).isZero();
                assertThat(img.getWidth()).isEqualTo(595);
                assertThat(img.getHeight()).isEqualTo(842);
            });
        }

        String texto = texto(relatorio.gerar(cabecalho(true), linhas));
        String segunda = texto.substring(texto.indexOf("Página 2"));
        assertThat(texto).contains("Etapa 01 ", "Etapa 40 ", "Página 1 de " + impresso.getPages().size());
        // Cabeçalho da tabela repete na página seguinte
        assertThat(texto.split("Responsável", -1)).hasSize(impresso.getPages().size() + 1);
        assertThat(segunda).isNotEmpty();
    }

    static List<RelatorioCronograma.Linha> quarentaEtapas() {
        List<RelatorioCronograma.Linha> linhas = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            boolean doCliente = i % 7 == 0;
            linhas.add(new RelatorioCronograma.Linha(String.format("Etapa %02d de implantação", i),
                    doCliente ? "Mercado São João" : "VR Software",
                    String.format("%02d/12/2026", 1 + i % 28), String.format("%02d/12/2026", 1 + i % 28), doCliente));
        }
        return linhas;
    }

    private static RelatorioCronograma.Cabecalho cabecalho(boolean temEtapaDoCliente) {
        return new RelatorioCronograma.Cabecalho("Mercado São João", "Implantação loja 2", "23/11/2026", "04/12/2026",
                10, "10/11/2026", temEtapaDoCliente);
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
