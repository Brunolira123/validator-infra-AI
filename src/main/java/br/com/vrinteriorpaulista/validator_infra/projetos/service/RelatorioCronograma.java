package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preenche o relatorios/cronograma.jrxml (sobre o timbrado relatorios/timbrado-confidencial.jpg) e exporta em PDF. O relatório é compilado uma vez, no primeiro uso.
 * Recebe só o que pode ir para o cliente: quem monta as linhas decide o conteúdo.
 */
@Component
public class RelatorioCronograma {

    static final String MODELO = "relatorios/cronograma.jrxml";
    private static final Locale PT_BR = new Locale("pt", "BR");

    private volatile JasperReport compilado;

    public record Cabecalho(String cliente, String projeto, String inicio, String termino, int diasUteis,
                            String emitidoEm, boolean temEtapaDoCliente) {}

    /** destaque = etapa do cliente (negrito). */
    public record Linha(String etapa, String quem, String inicio, String termino, boolean destaque) {}

    public byte[] gerar(Cabecalho cabecalho, List<Linha> linhas) {
        try {
            return JasperExportManager.exportReportToPdf(preencher(cabecalho, linhas));
        } catch (JRException e) {
            throw new FalhaNoRelatorio("Falha ao gerar o PDF do cronograma", e);
        }
    }

    /** Páginas prontas, antes de exportar (os testes conferem a posição dos elementos). */
    JasperPrint preencher(Cabecalho cabecalho, List<Linha> linhas) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("CLIENTE", cabecalho.cliente());
        parametros.put("PROJETO", cabecalho.projeto());
        parametros.put("INICIO", cabecalho.inicio());
        parametros.put("TERMINO", cabecalho.termino());
        parametros.put("DIAS_UTEIS", cabecalho.diasUteis());
        parametros.put("EMITIDO_EM", cabecalho.emitidoEm());
        parametros.put("TEM_ETAPA_CLIENTE", cabecalho.temEtapaDoCliente());
        parametros.put(JRParameter.REPORT_LOCALE, PT_BR);

        List<Map<String, ?>> registros = linhas.stream()
                .<Map<String, ?>>map(l -> Map.of(
                        "etapa", l.etapa(),
                        "quem", l.quem(),
                        "inicio", l.inicio(),
                        "termino", l.termino(),
                        "destaque", l.destaque()))
                .toList();
        try {
            return JasperFillManager.fillReport(relatorio(), parametros, new JRMapCollectionDataSource(registros));
        } catch (JRException e) {
            throw new FalhaNoRelatorio("Falha ao preencher o cronograma", e);
        }
    }

    private JasperReport relatorio() {
        JasperReport atual = compilado;
        if (atual == null) {
            synchronized (this) {
                if (compilado == null) {
                    compilado = compilar();
                }
                atual = compilado;
            }
        }
        return atual;
    }

    private static JasperReport compilar() {
        try (InputStream modelo = new ClassPathResource(MODELO).getInputStream()) {
            return JasperCompileManager.compileReport(modelo);
        } catch (IOException | JRException e) {
            throw new FalhaNoRelatorio("Falha ao compilar " + MODELO, e);
        }
    }

    /** Erro interno (500), não regra de negócio. */
    static class FalhaNoRelatorio extends RuntimeException {
        FalhaNoRelatorio(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
