package br.com.vrinteriorpaulista.validator_infra.projetos.planejador;

import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Alocacao;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Atividade;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Deslocamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Divisao;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Entrada;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Linha;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Ocupacao;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Resultado;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.TipoAlocacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Cenários da seção "Verificação" da 0.4 do Fluxo, mais capacidade entre projetos e atividade do cliente.
 * 23/11/2026 é segunda-feira.
 */
class PlanejadorCronogramaTest {

    private static final long ANA = 1L;
    private static final long BIA = 2L;
    private static final Map<Long, String> NOMES = Map.of(ANA, "Ana", BIA, "Bia");

    private static final LocalDate SEG = LocalDate.of(2026, 11, 23);
    private static final LocalDate TER = SEG.plusDays(1);
    private static final LocalDate QUA = SEG.plusDays(2);

    private final PlanejadorCronograma planejador = new PlanejadorCronograma(new BigDecimal("8"));

    @Test
    void deslocamentoDe3hNaSegundaReduzOTreinamentoPara5hEContinuaNaTerca() {
        Resultado r = calcular(SEG, List.of(atividade(1, "Treinamento", "12", ANA)),
                List.of(new Deslocamento(ANA, SEG, h("3"))), List.of());

        assertThat(servico(r, ANA)).containsExactly(aloc(SEG, "5"), aloc(TER, "7"));
        assertThat(linha(r, 1).fim()).isEqualTo(TER);
        assertThat(r.horasViagem()).isEqualByComparingTo("3");
        assertThat(r.horasServico()).isEqualByComparingTo("12");
        assertThat(r.alocacoes()).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo(TipoAlocacao.VIAGEM);
            assertThat(a.data()).isEqualTo(SEG);
            assertThat(a.horas()).isEqualByComparingTo("3");
            assertThat(a.atividadeId()).isNull();
        });
    }

    @Test
    void atividadeIndivisivelQueNaoCabeNaSegundaVaiParaTerca() {
        Resultado r = calcular(SEG, List.of(indivisivel(atividade(1, "Conversão", "6", ANA))),
                List.of(new Deslocamento(ANA, SEG, h("3"))), List.of());

        assertThat(servico(r, ANA)).containsExactly(aloc(TER, "6"));
        assertThat(linha(r, 1).inicio()).isEqualTo(TER);
    }

    @Test
    void doisAnalistasCom4hTrabalhamNoMesmoDia() {
        Atividade a = distribuida(atividade(1, "Implantação", "8", null), new Divisao(ANA, h("4")), new Divisao(BIA, h("4")));

        Resultado r = calcular(SEG, List.of(a), List.of(), List.of());

        assertThat(servico(r, ANA)).containsExactly(aloc(SEG, "4"));
        assertThat(servico(r, BIA)).containsExactly(aloc(SEG, "4"));
        assertThat(linha(r, 1).inicio()).isEqualTo(SEG);
        assertThat(linha(r, 1).fim()).isEqualTo(SEG);
        assertThat(r.diasUteis()).isEqualTo(1);
    }

    @Test
    void capacidadeCompartilhadaEntreAtividadesDoProjetoNaOrdemDaLista() {
        Resultado r = calcular(SEG, List.of(atividade(1, "A", "6", ANA), atividade(2, "B", "6", ANA)), List.of(), List.of());

        assertThat(servico(r, ANA)).containsExactly(aloc(SEG, "6"), aloc(SEG, "2"), aloc(TER, "4"));
        assertThat(linha(r, 1).fim()).isEqualTo(SEG);
        assertThat(linha(r, 2).inicio()).isEqualTo(SEG);
        assertThat(linha(r, 2).fim()).isEqualTo(TER);
    }

    @Test
    void dependenciaConcluidaLiberaNoProximoDiaUtilMesmoComCapacidadeNoDia() {
        Resultado r = calcular(SEG, List.of(atividade(1, "A", "4", ANA), depende(atividade(2, "B", "4", BIA), 1)),
                List.of(), List.of());

        assertThat(linha(r, 1).fim()).isEqualTo(SEG);
        assertThat(servico(r, BIA)).containsExactly(aloc(TER, "4"));
    }

    @Test
    void dependenciaDoPaiValeParaOsFilhos() {
        Resultado r = calcular(SEG, List.of(
                        atividade(1, "Kickoff", "8", ANA),
                        depende(atividade(2, "Fase 2", "0", null), 1),
                        filho(atividade(3, "Configurar", "2", BIA), 2)),
                List.of(), List.of());

        assertThat(servico(r, BIA)).containsExactly(aloc(TER, "2"));
        assertThat(linha(r, 2).inicio()).isEqualTo(TER);
    }

    @Test
    void dependenteDeAtividadeComFilhosEsperaOUltimoFilho() {
        Resultado r = calcular(SEG, List.of(
                        atividade(1, "Fase 1", null, null),
                        filho(atividade(2, "Parte 1", "8", ANA), 1),
                        filho(atividade(3, "Parte 2", "12", BIA), 1),
                        depende(atividade(4, "Fase 2", "2", ANA), 1)),
                List.of(), List.of());

        assertThat(linha(r, 1).fim()).isEqualTo(TER);
        assertThat(linha(r, 4).inicio()).isEqualTo(QUA);
    }

    @Test
    void pulaFimDeSemanaEFeriado() {
        LocalDate quinta = LocalDate.of(2026, 11, 19);
        LocalDate feriado = LocalDate.of(2026, 11, 20);

        Resultado r = calcular(quinta, List.of(atividade(1, "Conversão de banco", "16", ANA)), Set.of(feriado),
                List.of(), List.of());

        assertThat(servico(r, ANA)).containsExactly(aloc(quinta, "8"), aloc(SEG, "8"));
        assertThat(r.inicio()).isEqualTo(quinta);
        assertThat(r.termino()).isEqualTo(SEG);
        assertThat(r.diasUteis()).isEqualTo(2);
    }

    @Test
    void kickoffNoSabadoComecaNaSegunda() {
        Resultado r = calcular(SEG.minusDays(2), List.of(atividade(1, "Kickoff", "2", ANA)), List.of(), List.of());

        assertThat(linha(r, 1).inicio()).isEqualTo(SEG);
        assertThat(r.diasUteis()).isEqualTo(1);
    }

    @Test
    void atividadeComFilhosSomaOsFilhosSemHorasProprias() {
        Resultado r = calcular(SEG, List.of(
                        atividade(1, "Preparar loja", "2", ANA),
                        filho(atividade(2, "Cadastro", "4", ANA), 1),
                        filho(atividade(3, "Equipamentos", "3", BIA), 1)),
                List.of(), List.of());

        Linha resumo = linha(r, 1);
        assertThat(resumo.resumo()).isTrue();
        assertThat(resumo.horas()).isEqualByComparingTo("7");
        assertThat(resumo.inicio()).isEqualTo(SEG);
        assertThat(resumo.fim()).isEqualTo(SEG);
        assertThat(r.horasServico()).isEqualByComparingTo("7");
        assertThat(r.alocacoes()).noneMatch(a -> a.atividadeId() == 1L);
    }

    @Test
    void cicloDeDependenciasEhRecusado() {
        List<String> erros = erros(SEG, List.of(depende(atividade(1, "A", "1", ANA), 2), depende(atividade(2, "B", "1", ANA), 1)));

        assertThat(erros).containsExactly("Ciclo de dependências: A → B → A");
    }

    @Test
    void filhoQueDependeDoProprioPaiEhCiclo() {
        List<String> erros = erros(SEG, List.of(atividade(1, "Pai", null, null), depende(filho(atividade(2, "Filho", "1", ANA), 1), 1)));

        assertThat(erros).singleElement().asString().startsWith("Ciclo de dependências:");
    }

    @Test
    void cicloDeSubtarefasEhRecusado() {
        List<String> erros = erros(SEG, List.of(filho(atividade(1, "A", "1", ANA), 2), filho(atividade(2, "B", "1", ANA), 1)));

        assertThat(erros).singleElement().asString().startsWith("Ciclo de subtarefas:");
    }

    @Test
    void distribuicaoQueNaoSomaAsHorasEhRecusada() {
        Atividade a = distribuida(atividade(1, "Treinamento", "8", ANA), new Divisao(ANA, h("4")), new Divisao(BIA, h("2")));

        assertThat(erros(SEG, List.of(a)))
                .containsExactly("Atividade \"Treinamento\": a distribuição soma 6h, mas a atividade tem 8h");
    }

    @Test
    void indivisivelAcimaDaJornadaEhRecusada() {
        assertThat(erros(SEG, List.of(indivisivel(atividade(1, "Conversão", "10", ANA)))))
                .containsExactly("Atividade \"Conversão\": indivisível com 10h para Ana, acima da jornada de 8h");
    }

    @Test
    void errosVoltamTodosJuntos() {
        List<String> erros = erros(null, List.of(
                atividade(1, "Sem dono", "4", null),
                distribuida(atividade(2, "Dividida", "8", null), new Divisao(ANA, h("3"))),
                indivisivel(atividade(3, "Grande", "9.5", BIA)),
                depende(atividade(4, "Órfã", "1", ANA), 99)));

        assertThat(erros).containsExactlyInAnyOrder(
                "Informe a data do kickoff",
                "Atividade \"Sem dono\": sem responsável",
                "Atividade \"Dividida\": a distribuição soma 3h, mas a atividade tem 8h",
                "Atividade \"Grande\": indivisível com 9,5h para Bia, acima da jornada de 8h",
                "Atividade \"Órfã\": dependência não encontrada");
    }

    @Test
    void deslocamentosAcimaDaJornadaNoDiaSaoRecusados() {
        PlanejamentoInvalidoException e = catchThrowableOfType(PlanejamentoInvalidoException.class,
                () -> calcular(SEG, List.of(atividade(1, "A", "1", ANA)),
                        List.of(new Deslocamento(ANA, SEG, h("5")), new Deslocamento(ANA, SEG, h("4"))), List.of()));

        assertThat(e.getErros()).containsExactly("Deslocamentos de Ana em 23/11 somam 9h, acima da jornada de 8h");
    }

    @Test
    void capacidadeEntreProjetosDescontaOcupacaoExternaEListaConflitos() {
        List<Ocupacao> outros = List.of(new Ocupacao(ANA, SEG, h("8")), new Ocupacao(ANA, TER, h("4")),
                new Ocupacao(BIA, SEG, h("8")));

        Resultado r = calcular(SEG, List.of(atividade(1, "Treinamento", "8", ANA)), List.of(), outros);

        assertThat(servico(r, ANA)).containsExactly(aloc(TER, "4"), aloc(QUA, "4"));
        assertThat(r.conflitos()).singleElement().satisfies(c -> {
            assertThat(c.usuarioId()).isEqualTo(ANA);
            assertThat(c.semana()).isEqualTo(SEG);
            assertThat(c.horas()).isEqualByComparingTo("12");
            assertThat(c.mensagem()).isEqualTo("Ana: 12h em outro projeto na semana de 23/11");
        });
    }

    @Test
    void semOcupacaoExternaNaoHaConflito() {
        Resultado r = calcular(SEG, List.of(atividade(1, "A", "8", ANA)), List.of(),
                List.of(new Ocupacao(BIA, SEG, h("8"))));

        assertThat(r.conflitos()).isEmpty();
    }

    @Test
    void atividadeDoClienteOcupaDiasUteisSeguidosSemAnalistaNemHoras() {
        LocalDate quinta = LocalDate.of(2026, 11, 26);
        Atividade cliente = new Atividade(1, null, "Cliente confere os dados", h("5"), null, List.of(), false, true, 3, List.of());

        Resultado r = calcular(quinta, List.of(cliente, depende(atividade(2, "Ajustes", "8", ANA), 1)), List.of(), List.of());

        Linha linha = linha(r, 1);
        assertThat(linha.doCliente()).isTrue();
        assertThat(linha.inicio()).isEqualTo(quinta);
        assertThat(linha.fim()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(linha.horas()).isEqualByComparingTo("0");
        assertThat(r.alocacoes()).noneMatch(a -> a.atividadeId() != null && a.atividadeId() == 1L);
        assertThat(servico(r, ANA)).containsExactly(aloc(LocalDate.of(2026, 12, 1), "8"));
        assertThat(r.horasServico()).isEqualByComparingTo("8");
    }

    @Test
    void atividadeDoClienteSemDiasEhRecusada() {
        Atividade cliente = new Atividade(1, null, "Cliente", null, null, List.of(), false, true, 0, List.of());

        assertThat(erros(SEG, List.of(cliente)))
                .containsExactly("Atividade \"Cliente\": atividade do cliente precisa de pelo menos 1 dia");
    }

    @Test
    void atividadeSemHorasViraMarcoNoPrimeiroDiaUtil() {
        Resultado r = calcular(SEG.minusDays(1), List.of(atividade(1, "Marco", "0", null)), List.of(), List.of());

        assertThat(linha(r, 1).inicio()).isEqualTo(SEG);
        assertThat(r.alocacoes()).isEmpty();
    }

    @Test
    void passarDe10AnosEhErro() {
        Set<LocalDate> tudoFeriado = new HashSet<>();
        for (LocalDate d = SEG; d.isBefore(SEG.plusYears(11)); d = d.plusDays(1)) {
            tudoFeriado.add(d);
        }

        assertThatThrownBy(() -> calcular(SEG, List.of(atividade(1, "A", "8", ANA)), tudoFeriado, List.of(), List.of()))
                .isInstanceOf(PlanejamentoInvalidoException.class)
                .hasMessageContaining("10 anos");
    }

    @Test
    void diasUteisContamDoKickoffAoTerminoInclusive() {
        Resultado r = calcular(SEG, List.of(atividade(1, "A", "4", ANA), depende(atividade(2, "B", "40", ANA), 1)),
                List.of(), List.of());

        assertThat(r.inicio()).isEqualTo(SEG);
        assertThat(r.termino()).isEqualTo(SEG.plusDays(7));
        assertThat(r.diasUteis()).isEqualTo(6);
    }

    // ----- apoio -----

    private Resultado calcular(LocalDate kickoff, List<Atividade> atividades, List<Deslocamento> deslocamentos,
                               List<Ocupacao> outros) {
        return calcular(kickoff, atividades, Set.of(), deslocamentos, outros);
    }

    private Resultado calcular(LocalDate kickoff, List<Atividade> atividades, Set<LocalDate> feriados,
                               List<Deslocamento> deslocamentos, List<Ocupacao> outros) {
        return planejador.calcular(new Entrada(kickoff, atividades, feriados, deslocamentos, outros, NOMES));
    }

    private List<String> erros(LocalDate kickoff, List<Atividade> atividades) {
        PlanejamentoInvalidoException e = catchThrowableOfType(PlanejamentoInvalidoException.class,
                () -> calcular(kickoff, atividades, List.of(), List.of()));
        assertThat(e).as("deveria recusar o planejamento").isNotNull();
        return e.getErros();
    }

    private static Atividade atividade(long id, String nome, String horas, Long responsavel) {
        return new Atividade(id, null, nome, horas == null ? null : h(horas), responsavel, List.of(), false, false, 1, List.of());
    }

    private static Atividade filho(Atividade a, long pai) {
        return new Atividade(a.id(), pai, a.nome(), a.horas(), a.responsavelId(), a.distribuicao(), a.indivisivel(),
                a.doCliente(), a.diasCliente(), a.dependeDe());
    }

    private static Atividade depende(Atividade a, long... ids) {
        return new Atividade(a.id(), a.paiId(), a.nome(), a.horas(), a.responsavelId(), a.distribuicao(), a.indivisivel(),
                a.doCliente(), a.diasCliente(), java.util.Arrays.stream(ids).boxed().toList());
    }

    private static Atividade indivisivel(Atividade a) {
        return new Atividade(a.id(), a.paiId(), a.nome(), a.horas(), a.responsavelId(), a.distribuicao(), true,
                a.doCliente(), a.diasCliente(), a.dependeDe());
    }

    private static Atividade distribuida(Atividade a, Divisao... divisoes) {
        return new Atividade(a.id(), a.paiId(), a.nome(), a.horas(), a.responsavelId(), List.of(divisoes), a.indivisivel(),
                a.doCliente(), a.diasCliente(), a.dependeDe());
    }

    private static Linha linha(Resultado r, long id) {
        return r.linhas().stream().filter(l -> l.atividadeId() == id).findFirst().orElseThrow();
    }

    /** Alocações de serviço do analista, como (data, horas) com escala 2, na ordem do resultado. */
    private static List<String> servico(Resultado r, long usuario) {
        List<String> saida = new ArrayList<>();
        for (Alocacao a : r.alocacoes()) {
            if (a.usuarioId() == usuario && a.tipo() == TipoAlocacao.SERVICO) {
                saida.add(a.data() + " " + a.horas().setScale(2));
            }
        }
        return saida;
    }

    private static String aloc(LocalDate data, String horas) {
        return data + " " + h(horas).setScale(2);
    }

    private static BigDecimal h(String horas) {
        return new BigDecimal(horas);
    }
}
