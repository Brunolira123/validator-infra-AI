package br.com.vrinteriorpaulista.validator_infra.projetos.planejador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cronograma de um planejamento de implantação: porte do scheduler.js do Fluxo (0.4), sem Spring.
 * <p>
 * Regras: jornada fixa por analista e dia útil (seg–sex, fora dos feriados); deslocamentos e alocações de outros
 * projetos ocupam a capacidade; dependência concluída libera a atividade no próximo dia útil (as dependências do
 * pai valem para os filhos); atividade com filhos é resumo (soma dos filhos, sem horas próprias); sem distribuição,
 * todas as horas vão para o responsável; atividade do cliente ocupa dias úteis seguidos, sem analista e sem horas.
 * A ordem da lista desempata atividades independentes disputando o mesmo analista.
 * <p>
 * As horas são calculadas em centésimos de hora (inteiros), então a divisão sempre fecha sem erro de arredondamento.
 */
public final class PlanejadorCronograma {

    /** Dias corridos que uma atividade pode procurar capacidade: 10 anos, como no Fluxo. */
    static final int LIMITE_DIAS = 3660;

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final long jornada;

    public PlanejadorCronograma(BigDecimal horasDia) {
        if (horasDia == null || horasDia.signum() <= 0) {
            throw new IllegalArgumentException("Jornada diária inválida: " + horasDia);
        }
        this.jornada = centesimos(horasDia);
    }

    public enum TipoAlocacao { SERVICO, VIAGEM }

    /**
     * @param ocupacaoExterna horas já alocadas aos analistas em outros planejamentos (descontadas da capacidade)
     * @param nomesUsuarios   só para as mensagens; sem o nome, aparece "Usuário {id}"
     */
    public record Entrada(LocalDate kickoff, List<Atividade> atividades, Set<LocalDate> feriados,
                          List<Deslocamento> deslocamentos, List<Ocupacao> ocupacaoExterna,
                          Map<Long, String> nomesUsuarios) {
        public Entrada {
            atividades = atividades == null ? List.of() : atividades;
            feriados = feriados == null ? Set.of() : feriados;
            deslocamentos = deslocamentos == null ? List.of() : deslocamentos;
            ocupacaoExterna = ocupacaoExterna == null ? List.of() : ocupacaoExterna;
            nomesUsuarios = nomesUsuarios == null ? Map.of() : nomesUsuarios;
        }
    }

    public record Atividade(long id, Long paiId, String nome, BigDecimal horas, Long responsavelId,
                            List<Divisao> distribuicao, boolean indivisivel, boolean doCliente, int diasCliente,
                            List<Long> dependeDe) {
        public Atividade {
            distribuicao = distribuicao == null ? List.of() : distribuicao;
            dependeDe = dependeDe == null ? List.of() : dependeDe;
        }
    }

    public record Divisao(Long usuarioId, BigDecimal horas) {}

    public record Deslocamento(Long usuarioId, LocalDate data, BigDecimal horas) {}

    public record Ocupacao(long usuarioId, LocalDate data, BigDecimal horas) {}

    public record Resultado(LocalDate inicio, LocalDate termino, int diasUteis, BigDecimal horasServico,
                            BigDecimal horasViagem, List<Linha> linhas, List<Alocacao> alocacoes,
                            List<Conflito> conflitos) {}

    public record Linha(long atividadeId, LocalDate inicio, LocalDate fim, BigDecimal horas, boolean resumo,
                        boolean doCliente) {}

    /** atividadeId é null nas viagens. */
    public record Alocacao(long usuarioId, Long atividadeId, LocalDate data, BigDecimal horas, TipoAlocacao tipo) {}

    /** Semana (segunda-feira) em que o analista tinha horas de outros projetos nos dias que este cálculo usou. */
    public record Conflito(long usuarioId, LocalDate semana, BigDecimal horas, String mensagem) {}

    public Resultado calcular(Entrada entrada) {
        return new Calculo(entrada).executar();
    }

    private record Chave(long usuarioId, LocalDate data) {}

    private record Feito(LocalDate inicio, LocalDate fim, long horas, boolean resumo, boolean doCliente) {}

    private final class Calculo {

        private final Entrada entrada;
        private final Map<Long, Atividade> porId = new LinkedHashMap<>();
        private final Map<Long, List<Atividade>> filhos = new HashMap<>();
        private final Map<Chave, Long> viagem = new HashMap<>();
        private final Map<Chave, Long> externo = new HashMap<>();
        private final Map<Chave, Long> carga = new HashMap<>();
        private final Set<Chave> diasComOutroProjeto = new HashSet<>();
        private final Map<Long, Feito> feito = new HashMap<>();
        private final List<Alocacao> alocacoes = new ArrayList<>();

        Calculo(Entrada entrada) {
            this.entrada = entrada;
            for (Atividade a : entrada.atividades()) {
                porId.put(a.id(), a);
            }
            for (Atividade a : entrada.atividades()) {
                if (a.paiId() != null && porId.containsKey(a.paiId())) {
                    filhos.computeIfAbsent(a.paiId(), k -> new ArrayList<>()).add(a);
                }
            }
        }

        Resultado executar() {
            validar();
            for (Ocupacao o : entrada.ocupacaoExterna()) {
                externo.merge(new Chave(o.usuarioId(), o.data()), centesimos(o.horas()), Long::sum);
            }
            agendar();
            return resultado();
        }

        private void validar() {
            List<String> erros = new ArrayList<>();
            if (entrada.kickoff() == null) {
                erros.add("Informe a data do kickoff");
            }
            if (porId.size() != entrada.atividades().size()) {
                erros.add("Há atividades repetidas no planejamento");
            }
            for (Atividade a : entrada.atividades()) {
                if (a.paiId() != null && !porId.containsKey(a.paiId())) {
                    erros.add(rotulo(a) + ": atividade principal não encontrada");
                }
                for (Long dep : a.dependeDe()) {
                    if (!porId.containsKey(dep)) {
                        erros.add(rotulo(a) + ": dependência não encontrada");
                    }
                }
                if (!filhos.containsKey(a.id())) {
                    validarFolha(a, erros);
                }
            }
            validarDeslocamentos(erros);
            if (validarHierarquia(erros)) {
                validarCiclos(erros);
            }
            if (!erros.isEmpty()) {
                throw new PlanejamentoInvalidoException(erros);
            }
        }

        private void validarFolha(Atividade a, List<String> erros) {
            if (a.doCliente()) {
                if (a.diasCliente() < 1) {
                    erros.add(rotulo(a) + ": atividade do cliente precisa de pelo menos 1 dia");
                }
                return;
            }
            long horas = horasDe(a);
            if (horas < 0) {
                erros.add(rotulo(a) + ": horas não podem ser negativas");
                return;
            }
            if (a.distribuicao().isEmpty()) {
                if (horas > 0 && a.responsavelId() == null) {
                    erros.add(rotulo(a) + ": sem responsável");
                } else if (a.indivisivel() && horas > jornada) {
                    erros.add(rotulo(a) + ": indivisível com " + formatar(horas) + " para " + nome(a.responsavelId())
                            + ", acima da jornada de " + formatar(jornada));
                }
                return;
            }
            long soma = 0;
            boolean divisaoValida = true;
            for (Divisao d : a.distribuicao()) {
                if (d.usuarioId() == null || d.horas() == null || d.horas().signum() < 0) {
                    erros.add(rotulo(a) + ": distribuição com analista ou horas inválidos");
                    divisaoValida = false;
                    continue;
                }
                long h = centesimos(d.horas());
                soma += h;
                if (a.indivisivel() && h > jornada) {
                    erros.add(rotulo(a) + ": indivisível com " + formatar(h) + " para " + nome(d.usuarioId())
                            + ", acima da jornada de " + formatar(jornada));
                }
            }
            if (divisaoValida && soma != horas) {
                erros.add(rotulo(a) + ": a distribuição soma " + formatar(soma) + ", mas a atividade tem "
                        + formatar(horas));
            }
        }

        private void validarDeslocamentos(List<String> erros) {
            for (Deslocamento d : entrada.deslocamentos()) {
                if (d.usuarioId() == null || d.data() == null) {
                    erros.add("Deslocamento sem analista ou sem data");
                    continue;
                }
                if (d.horas() == null || d.horas().signum() < 0 || centesimos(d.horas()) > jornada) {
                    erros.add("Deslocamento de " + nome(d.usuarioId()) + " em " + d.data().format(DIA_MES)
                            + ": horas devem ficar entre 0 e " + formatar(jornada));
                    continue;
                }
                viagem.merge(new Chave(d.usuarioId(), d.data()), centesimos(d.horas()), Long::sum);
            }
            viagem.entrySet().stream()
                    .filter(e -> e.getValue() > jornada)
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Chave::data).thenComparing(Chave::usuarioId)))
                    .forEach(e -> erros.add("Deslocamentos de " + nome(e.getKey().usuarioId()) + " em "
                            + e.getKey().data().format(DIA_MES) + " somam " + formatar(e.getValue())
                            + ", acima da jornada de " + formatar(jornada)));
        }

        /** Pai que é descendente do próprio filho. Com ele, o resto da validação entraria em laço. */
        private boolean validarHierarquia(List<String> erros) {
            Set<Long> emCiclo = new HashSet<>();
            for (Atividade a : entrada.atividades()) {
                List<Long> caminho = new ArrayList<>();
                Long atual = a.id();
                while (atual != null && porId.containsKey(atual) && !caminho.contains(atual)) {
                    caminho.add(atual);
                    atual = porId.get(atual).paiId();
                }
                if (atual != null && caminho.contains(atual) && emCiclo.add(atual)) {
                    List<Long> ciclo = new ArrayList<>(caminho.subList(caminho.indexOf(atual), caminho.size()));
                    emCiclo.addAll(ciclo);
                    ciclo.add(atual);
                    erros.add("Ciclo de subtarefas: " + nomes(ciclo));
                }
            }
            return emCiclo.isEmpty();
        }

        /**
         * Arestas: dependências, filhos (o pai só termina depois deles) e dependências dos ancestrais (o filho
         * espera por elas). Assim, filho que depende do próprio pai também é ciclo.
         */
        private void validarCiclos(List<String> erros) {
            Map<Long, Integer> cor = new HashMap<>();
            Set<Set<Long>> vistos = new HashSet<>();
            for (Atividade a : entrada.atividades()) {
                if (!cor.containsKey(a.id())) {
                    visitar(a.id(), cor, new ArrayList<>(), vistos, erros);
                }
            }
        }

        private void visitar(long id, Map<Long, Integer> cor, List<Long> caminho, Set<Set<Long>> vistos,
                             List<String> erros) {
            cor.put(id, 1);
            caminho.add(id);
            for (Long proximo : arestas(porId.get(id))) {
                Integer c = cor.get(proximo);
                if (c == null) {
                    visitar(proximo, cor, caminho, vistos, erros);
                } else if (c == 1) {
                    List<Long> ciclo = new ArrayList<>(caminho.subList(caminho.indexOf(proximo), caminho.size()));
                    if (vistos.add(new HashSet<>(ciclo))) {
                        ciclo.add(proximo);
                        erros.add("Ciclo de dependências: " + nomes(ciclo));
                    }
                }
            }
            caminho.remove(caminho.size() - 1);
            cor.put(id, 2);
        }

        private Set<Long> arestas(Atividade a) {
            Set<Long> saida = new LinkedHashSet<>();
            a.dependeDe().stream().filter(porId::containsKey).forEach(saida::add);
            filhos.getOrDefault(a.id(), List.of()).forEach(f -> saida.add(f.id()));
            for (Long p = a.paiId(); p != null && porId.containsKey(p); p = porId.get(p).paiId()) {
                porId.get(p).dependeDe().stream().filter(porId::containsKey).forEach(saida::add);
            }
            return saida;
        }

        private void agendar() {
            Set<Long> restantes = new LinkedHashSet<>(porId.keySet());
            int voltas = 0;
            while (!restantes.isEmpty()) {
                if (++voltas > porId.size() + 1) {
                    throw new PlanejamentoInvalidoException(List.of("Não foi possível ordenar as atividades"));
                }
                boolean avancou = false;
                for (Atividade t : entrada.atividades()) {
                    if (!restantes.contains(t.id())) continue;
                    List<Atividade> fs = filhos.getOrDefault(t.id(), List.of());
                    if (t.dependeDe().stream().anyMatch(d -> !feito.containsKey(d))
                            || fs.stream().anyMatch(f -> !feito.containsKey(f.id()))) {
                        continue;
                    }
                    if (!fs.isEmpty()) {
                        feito.put(t.id(), resumo(fs));
                        restantes.remove(t.id());
                        avancou = true;
                        continue;
                    }
                    LocalDate liberada = entrada.kickoff();
                    for (Long d : t.dependeDe()) {
                        liberada = max(liberada, feito.get(d).fim().plusDays(1));
                    }
                    boolean pendente = false;
                    for (Long p = t.paiId(); p != null && !pendente; p = porId.get(p).paiId()) {
                        for (Long d : porId.get(p).dependeDe()) {
                            if (!feito.containsKey(d)) {
                                pendente = true;
                                break;
                            }
                            liberada = max(liberada, feito.get(d).fim().plusDays(1));
                        }
                    }
                    if (pendente) continue;

                    feito.put(t.id(), t.doCliente() ? agendarCliente(t, liberada) : agendarAnalistas(t, liberada));
                    restantes.remove(t.id());
                    avancou = true;
                }
                if (!avancou) {
                    throw new PlanejamentoInvalidoException(List.of("Dependências não resolvidas"));
                }
            }
        }

        private Feito resumo(List<Atividade> fs) {
            LocalDate inicio = null;
            LocalDate fim = null;
            long horas = 0;
            for (Atividade f : fs) {
                Feito r = feito.get(f.id());
                inicio = inicio == null || r.inicio().isBefore(inicio) ? r.inicio() : inicio;
                fim = fim == null || r.fim().isAfter(fim) ? r.fim() : fim;
                horas += r.horas();
            }
            return new Feito(inicio, fim, horas, true, false);
        }

        private Feito agendarCliente(Atividade t, LocalDate liberada) {
            LocalDate inicio = proximoUtil(liberada, t);
            LocalDate fim = inicio;
            for (int i = 1; i < t.diasCliente(); i++) {
                fim = proximoUtil(fim.plusDays(1), t);
            }
            return new Feito(inicio, fim, 0, false, true);
        }

        private Feito agendarAnalistas(Atividade t, LocalDate liberada) {
            List<Divisao> divisoes = t.distribuicao();
            if (divisoes.isEmpty()) {
                divisoes = t.responsavelId() == null ? List.of() : List.of(new Divisao(t.responsavelId(), t.horas()));
            }
            LocalDate inicio = null;
            LocalDate fim = null;
            for (Divisao divisao : divisoes) {
                long horas = divisao.horas() == null ? 0 : centesimos(divisao.horas());
                long usuario = divisao.usuarioId();
                LocalDate cursor = liberada;
                int tentativas = 0;
                while (horas > 0) {
                    if (++tentativas > LIMITE_DIAS) {
                        throw limiteExcedido(t);
                    }
                    LocalDate data = cursor;
                    cursor = cursor.plusDays(1);
                    if (!util(data)) continue;
                    Chave chave = new Chave(usuario, data);
                    long deOutros = externo.getOrDefault(chave, 0L);
                    if (deOutros > 0) {
                        diasComOutroProjeto.add(chave);
                    }
                    long disponivel = jornada - viagem.getOrDefault(chave, 0L) - carga.getOrDefault(chave, 0L) - deOutros;
                    if (disponivel <= 0 || t.indivisivel() && disponivel < horas) continue;
                    long atribuido = Math.min(disponivel, horas);
                    carga.merge(chave, atribuido, Long::sum);
                    horas -= atribuido;
                    alocacoes.add(new Alocacao(usuario, t.id(), data, horas(atribuido), TipoAlocacao.SERVICO));
                    inicio = inicio == null || data.isBefore(inicio) ? data : inicio;
                    fim = fim == null || data.isAfter(fim) ? data : fim;
                }
            }
            if (inicio == null) {
                inicio = proximoUtil(liberada, t);
                fim = inicio;
            }
            return new Feito(inicio, fim, horasDe(t), false, false);
        }

        private LocalDate proximoUtil(LocalDate data, Atividade t) {
            LocalDate d = data;
            for (int i = 0; !util(d); i++) {
                if (i > LIMITE_DIAS) {
                    throw limiteExcedido(t);
                }
                d = d.plusDays(1);
            }
            return d;
        }

        private PlanejamentoInvalidoException limiteExcedido(Atividade t) {
            return new PlanejamentoInvalidoException(List.of(rotulo(t)
                    + ": não há capacidade nos próximos 10 anos (planejamento excedeu o limite)"));
        }

        private boolean util(LocalDate d) {
            DayOfWeek dia = d.getDayOfWeek();
            return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !entrada.feriados().contains(d);
        }

        private Resultado resultado() {
            List<Linha> linhas = new ArrayList<>();
            LocalDate inicio = null;
            LocalDate termino = null;
            long horasServico = 0;
            for (Atividade t : entrada.atividades()) {
                Feito r = feito.get(t.id());
                linhas.add(new Linha(t.id(), r.inicio(), r.fim(), horas(r.horas()), r.resumo(), r.doCliente()));
                inicio = inicio == null || r.inicio().isBefore(inicio) ? r.inicio() : inicio;
                termino = termino == null || r.fim().isAfter(termino) ? r.fim() : termino;
                if (!r.resumo() && !r.doCliente()) {
                    horasServico += r.horas();
                }
            }
            if (inicio == null) {
                inicio = entrada.kickoff();
                termino = entrada.kickoff();
            }
            int diasUteis = 0;
            for (LocalDate d = entrada.kickoff(); !d.isAfter(termino); d = d.plusDays(1)) {
                if (util(d)) diasUteis++;
            }

            long horasViagem = 0;
            for (Deslocamento d : entrada.deslocamentos()) {
                long h = centesimos(d.horas());
                horasViagem += h;
                if (h > 0) {
                    alocacoes.add(new Alocacao(d.usuarioId(), null, d.data(), horas(h), TipoAlocacao.VIAGEM));
                }
            }
            alocacoes.sort(Comparator.comparing(Alocacao::data).thenComparing(Alocacao::usuarioId)
                    .thenComparing(Alocacao::tipo));
            return new Resultado(inicio, termino, diasUteis, horas(horasServico), horas(horasViagem),
                    List.copyOf(linhas), List.copyOf(alocacoes), conflitos());
        }

        private List<Conflito> conflitos() {
            Map<Chave, Long> porSemana = new TreeMap<>(Comparator.comparing(Chave::data).thenComparing(Chave::usuarioId));
            for (Chave dia : diasComOutroProjeto) {
                LocalDate segunda = dia.data().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                porSemana.merge(new Chave(dia.usuarioId(), segunda), externo.get(dia), Long::sum);
            }
            return porSemana.entrySet().stream()
                    .map(e -> new Conflito(e.getKey().usuarioId(), e.getKey().data(), horas(e.getValue()),
                            nome(e.getKey().usuarioId()) + ": " + formatar(e.getValue())
                                    + " em outro projeto na semana de " + e.getKey().data().format(DIA_MES)))
                    .toList();
        }

        private String rotulo(Atividade a) {
            return "Atividade \"" + a.nome() + "\"";
        }

        private String nome(Long usuarioId) {
            return entrada.nomesUsuarios().getOrDefault(usuarioId, "Usuário " + usuarioId);
        }

        private String nomes(List<Long> ids) {
            return String.join(" → ", ids.stream().map(id -> porId.get(id).nome()).toList());
        }
    }

    private static long horasDe(Atividade a) {
        return a.horas() == null ? 0 : centesimos(a.horas());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    static long centesimos(BigDecimal horas) {
        return horas.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static BigDecimal horas(long centesimos) {
        return BigDecimal.valueOf(centesimos, 2);
    }

    /** 1800 → "18h"; 450 → "4,5h". */
    static String formatar(long centesimos) {
        return horas(centesimos).stripTrailingZeros().toPlainString().replace('.', ',') + "h";
    }
}
