package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Cliente;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.CalculoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.LinhaCalculoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Planejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.PlanejamentoAtividade;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PDF do cronograma para o cliente, a partir do último cálculo (que precisa estar em dia). Leva só etapas, quem
 * faz (VR ou o cliente) e datas: nunca horas, custos, analistas nem observações internas. Essa filtragem é feita
 * aqui, nas linhas que vão para o relatório.
 */
@Service
@Slf4j
public class CronogramaPdfService {

    static final String VR = "VR";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Comparator<PlanejamentoAtividade> ORDEM =
            Comparator.comparing(PlanejamentoAtividade::getOrdem).thenComparing(PlanejamentoAtividade::getId);

    private final AcessoProjetos acesso;
    private final CalculoPlanejamentoService calculos;
    private final RelatorioCronograma relatorio;

    public CronogramaPdfService(AcessoProjetos acesso, CalculoPlanejamentoService calculos, RelatorioCronograma relatorio) {
        this.acesso = acesso;
        this.calculos = calculos;
        this.relatorio = relatorio;
    }

    public record Pdf(String nomeArquivo, byte[] conteudo) {}

    /**
     * marcarEnviado: depois de gerar, passa o planejamento para ENVIADO (só PROJETOS/ADMIN/DIRETORIA; a partir de
     * EM_PLANEJAMENTO, ou já ENVIADO). Sem cálculo ou com cálculo desatualizado: 409.
     */
    @Transactional
    public Pdf gerar(Long id, boolean marcarEnviado) {
        if (marcarEnviado && !acesso.editor()) {
            throw new AccessDeniedException("Só PROJETOS, ADMIN e DIRETORIA marcam o planejamento como enviado");
        }
        Planejamento p = acesso.visivel(id);
        if (p.getCalculo() == null) {
            throw new IllegalStateException("Calcule o planejamento antes de gerar o cronograma");
        }
        if (Boolean.TRUE.equals(p.getCalculoDesatualizado())) {
            throw new IllegalStateException("O cálculo está desatualizado: recalcule antes de gerar o cronograma");
        }
        boolean mudarSituacao = marcarEnviado && p.getSituacao() != SituacaoPlanejamento.ENVIADO;
        if (mudarSituacao && !p.getSituacao().podeIrPara(SituacaoPlanejamento.ENVIADO)) {
            throw new IllegalStateException("Não é possível passar de " + p.getSituacao() + " para ENVIADO");
        }

        CalculoResponse calculo = calculos.ler(p.getCalculo());
        String cliente = nomeCliente(p.getCliente());
        List<RelatorioCronograma.Linha> linhas = linhas(p, calculo, cliente);
        byte[] pdf = relatorio.gerar(new RelatorioCronograma.Cabecalho(
                cliente, p.getNome(), formatar(calculo.inicio()), formatar(calculo.termino()), calculo.diasUteis(),
                formatar(LocalDate.now()), linhas.stream().anyMatch(RelatorioCronograma.Linha::destaque)), linhas);

        if (mudarSituacao) {
            log.info("Planejamento {}: {} → ENVIADO ao gerar o cronograma", p.getId(), p.getSituacao());
            p.setSituacao(SituacaoPlanejamento.ENVIADO);
        }
        return new Pdf("cronograma-" + slug(p.getNome()) + ".pdf", pdf);
    }

    /** Hierarquia em pré-ordem, com recuo nas subtarefas. */
    private static List<RelatorioCronograma.Linha> linhas(Planejamento p, CalculoResponse calculo, String cliente) {
        Map<Long, LinhaCalculoResponse> datas = calculo.atividades().stream()
                .collect(Collectors.toMap(LinhaCalculoResponse::atividadeId, Function.identity()));
        Map<Long, List<PlanejamentoAtividade>> filhos = new HashMap<>();
        List<PlanejamentoAtividade> raizes = new ArrayList<>();
        for (PlanejamentoAtividade a : p.getAtividades()) {
            if (a.getPai() == null) {
                raizes.add(a);
            } else {
                filhos.computeIfAbsent(a.getPai().getId(), k -> new ArrayList<>()).add(a);
            }
        }
        List<RelatorioCronograma.Linha> linhas = new ArrayList<>();
        raizes.stream().sorted(ORDEM).forEach(a -> adicionar(a, 0, filhos, datas, cliente, linhas));
        return linhas;
    }

    private static void adicionar(PlanejamentoAtividade a, int nivel, Map<Long, List<PlanejamentoAtividade>> filhos,
                                  Map<Long, LinhaCalculoResponse> datas, String cliente,
                                  List<RelatorioCronograma.Linha> linhas) {
        LinhaCalculoResponse datasDaEtapa = datas.get(a.getId());
        if (datasDaEtapa != null) {
            Set<Boolean> doCliente = new LinkedHashSet<>();
            folhas(a, filhos, doCliente);
            String quem = doCliente.size() > 1 ? VR + " e " + cliente : doCliente.contains(true) ? cliente : VR;
            linhas.add(new RelatorioCronograma.Linha(" ".repeat(nivel) + a.getNome(), quem,
                    formatar(datasDaEtapa.inicio()), formatar(datasDaEtapa.fim()),
                    doCliente.size() == 1 && doCliente.contains(true)));
        }
        filhos.getOrDefault(a.getId(), List.of()).stream().sorted(ORDEM)
                .forEach(f -> adicionar(f, nivel + 1, filhos, datas, cliente, linhas));
    }

    private static void folhas(PlanejamentoAtividade a, Map<Long, List<PlanejamentoAtividade>> filhos, Set<Boolean> saida) {
        List<PlanejamentoAtividade> fs = filhos.getOrDefault(a.getId(), List.of());
        if (fs.isEmpty()) {
            saida.add(Boolean.TRUE.equals(a.getDoCliente()));
        }
        fs.forEach(f -> folhas(f, filhos, saida));
    }

    private static String nomeCliente(Cliente c) {
        return c.getNomeFantasia() != null && !c.getNomeFantasia().isBlank() ? c.getNomeFantasia() : c.getRazaoSocial();
    }

    private static String formatar(LocalDate data) {
        return data != null ? data.format(DATA) : "";
    }

    static String slug(String texto) {
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "planejamento" : s;
    }
}
