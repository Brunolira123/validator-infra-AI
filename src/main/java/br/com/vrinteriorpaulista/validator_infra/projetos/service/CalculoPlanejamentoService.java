package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.entity.Usuario;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.AlocacaoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.CalculoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.CargaResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.ConflitoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.LinhaCalculoResponse;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Distribuicao;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.FeriadoExtra;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Planejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.PlanejamentoAtividade;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.SituacaoPlanejamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Atividade;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Deslocamento;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Divisao;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Entrada;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Ocupacao;
import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma.Resultado;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.PlanejamentoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta a entrada do {@link PlanejadorCronograma} a partir do planejamento, desconta a agenda dos outros projetos e
 * salva o último cálculo (JSON). Também soma a carga dos analistas para a visão geral.
 */
@Service
@Slf4j
public class CalculoPlanejamentoService {

    static final int LIMITE_DIAS_CARGA = 366;

    private final PlanejamentoRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final FeriadoService feriados;
    private final AcessoProjetos acesso;
    private final PlanejadorCronograma planejador;
    private final ObjectMapper objectMapper;

    public CalculoPlanejamentoService(PlanejamentoRepository repo, UsuarioRepository usuarioRepo, FeriadoService feriados,
                                      AcessoProjetos acesso, PlanejadorCronograma planejador, ObjectMapper objectMapper) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
        this.feriados = feriados;
        this.acesso = acesso;
        this.planejador = planejador;
        this.objectMapper = objectMapper;
    }

    /** Erros de validação vêm todos juntos em PlanejamentoInvalidoException (400) e nada é salvo. */
    @Transactional
    public CalculoResponse calcular(Long id) {
        Planejamento p = acesso.editavel(id);
        Map<Long, String> nomes = new HashMap<>();
        List<Atividade> atividades = new ArrayList<>();
        for (PlanejamentoAtividade a : p.getAtividades()) {
            atividades.add(paraPlanejador(a, nomes));
        }
        List<Deslocamento> deslocamentos = p.getDeslocamentos().stream()
                .map(d -> {
                    nomes.put(d.getUsuario().getId(), d.getUsuario().getNome());
                    return new Deslocamento(d.getUsuario().getId(), d.getData(), d.getHoras());
                })
                .toList();
        Set<LocalDate> diasSemExpediente = new HashSet<>(feriados.datasPara(p.getCliente()));
        p.getFeriadosExtras().stream().map(FeriadoExtra::getData).forEach(diasSemExpediente::add);

        Resultado r = planejador.calcular(new Entrada(p.getKickoff(), atividades, diasSemExpediente, deslocamentos,
                ocupacaoDosOutros(p.getId()), nomes));

        CalculoResponse calculo = montar(p, r, nomes);
        p.setCalculo(escrever(calculo));
        p.setCalculadoEm(calculo.calculadoEm());
        p.setCalculoDesatualizado(false);
        p.setInicioCalculado(calculo.inicio());
        p.setTerminoCalculado(calculo.termino());
        log.info("Planejamento {} calculado: {} a {}, {}h de serviço, {} conflito(s) com outros projetos",
                p.getId(), calculo.inicio(), calculo.termino(), calculo.horasServico(), calculo.conflitos().size());
        return calculo;
    }

    /** Horas por analista e dia, somando o último cálculo dos planejamentos EM_PLANEJAMENTO, ENVIADO e CRIADO_ARTIA. */
    @Transactional(readOnly = true)
    public List<CargaResponse> carga(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new IllegalArgumentException("'ate' não pode ser antes de 'de'");
        }
        if (ChronoUnit.DAYS.between(de, ate) >= LIMITE_DIAS_CARGA) {
            throw new IllegalArgumentException("Período de no máximo " + LIMITE_DIAS_CARGA + " dias");
        }
        record Dia(Long usuarioId, LocalDate data) {}
        Map<Dia, Map<Long, BigDecimal>> porDia = new HashMap<>();
        Map<Long, String> nomesPlanejamento = new HashMap<>();
        for (PlanejamentoRepository.CalculoSalvo salvo : repo.calculosQueOcupamAgenda(SituacaoPlanejamento.OCUPAM_AGENDA, 0L)) {
            CalculoResponse c = lerSemFalhar(salvo);
            if (c == null) continue;
            nomesPlanejamento.put(salvo.getId(), salvo.getNome());
            for (AlocacaoResponse a : c.alocacoes()) {
                if (a.data().isBefore(de) || a.data().isAfter(ate)) continue;
                porDia.computeIfAbsent(new Dia(a.usuarioId(), a.data()), k -> new TreeMap<>())
                        .merge(salvo.getId(), a.horas(), BigDecimal::add);
            }
        }
        Set<Long> usuarios = porDia.keySet().stream().map(Dia::usuarioId).collect(Collectors.toSet());
        Map<Long, String> nomes = usuarioRepo.findAllById(usuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
        return porDia.entrySet().stream()
                .map(e -> new CargaResponse(e.getKey().usuarioId(),
                        nomes.getOrDefault(e.getKey().usuarioId(), "Usuário " + e.getKey().usuarioId()),
                        e.getKey().data(),
                        e.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().entrySet().stream()
                                .map(p -> new CargaResponse.CargaPlanejamentoResponse(p.getKey(),
                                        nomesPlanejamento.get(p.getKey()), p.getValue()))
                                .toList()))
                .sorted(Comparator.comparing(CargaResponse::usuarioNome).thenComparing(CargaResponse::usuarioId)
                        .thenComparing(CargaResponse::data))
                .toList();
    }

    public CalculoResponse ler(String json) {
        return objectMapper.readValue(json, CalculoResponse.class);
    }

    private List<Ocupacao> ocupacaoDosOutros(Long planejamentoId) {
        List<Ocupacao> ocupacao = new ArrayList<>();
        for (PlanejamentoRepository.CalculoSalvo salvo : repo.calculosQueOcupamAgenda(SituacaoPlanejamento.OCUPAM_AGENDA, planejamentoId)) {
            CalculoResponse c = lerSemFalhar(salvo);
            if (c == null) continue;
            for (AlocacaoResponse a : c.alocacoes()) {
                ocupacao.add(new Ocupacao(a.usuarioId(), a.data(), a.horas()));
            }
        }
        return ocupacao;
    }

    private CalculoResponse lerSemFalhar(PlanejamentoRepository.CalculoSalvo salvo) {
        try {
            return ler(salvo.getCalculo());
        } catch (JacksonException e) {
            log.warn("Cálculo salvo do planejamento {} ilegível, ignorado: {}", salvo.getId(), e.getMessage());
            return null;
        }
    }

    private String escrever(CalculoResponse calculo) {
        return objectMapper.writeValueAsString(calculo);
    }

    private static Atividade paraPlanejador(PlanejamentoAtividade a, Map<Long, String> nomes) {
        if (a.getResponsavel() != null) {
            nomes.put(a.getResponsavel().getId(), a.getResponsavel().getNome());
        }
        List<Divisao> divisoes = new ArrayList<>();
        for (Distribuicao d : a.getDistribuicao()) {
            nomes.put(d.getUsuario().getId(), d.getUsuario().getNome());
            divisoes.add(new Divisao(d.getUsuario().getId(), d.getHoras()));
        }
        return new Atividade(a.getId(), a.getPai() != null ? a.getPai().getId() : null, a.getNome(), a.getHoras(),
                a.getResponsavel() != null ? a.getResponsavel().getId() : null, divisoes,
                Boolean.TRUE.equals(a.getIndivisivel()), Boolean.TRUE.equals(a.getDoCliente()), a.getDiasCliente(),
                a.getDependeDe().stream().map(PlanejamentoAtividade::getId).sorted().toList());
    }

    private static CalculoResponse montar(Planejamento p, Resultado r, Map<Long, String> nomes) {
        Map<Long, String> nomesAtividades = p.getAtividades().stream()
                .collect(Collectors.toMap(PlanejamentoAtividade::getId, PlanejamentoAtividade::getNome, (a, b) -> a,
                        LinkedHashMap::new));
        Function<Long, String> nome = id -> nomes.getOrDefault(id, "Usuário " + id);
        BigDecimal custo = r.horasServico().multiply(zeroSeNull(p.getCustoHoraServico()))
                .add(r.horasViagem().multiply(zeroSeNull(p.getCustoHoraViagem())))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldo = p.getHorasContratadas() != null ? p.getHorasContratadas().subtract(r.horasServico()) : null;
        return new CalculoResponse(
                LocalDateTime.now(), p.getKickoff(), r.inicio(), r.termino(), r.diasUteis(), r.horasServico(),
                r.horasViagem(), custo, p.getHorasContratadas(), saldo,
                r.linhas().stream()
                        .map(l -> new LinhaCalculoResponse(l.atividadeId(), nomesAtividades.get(l.atividadeId()),
                                l.inicio(), l.fim(), l.horas(), l.resumo(), l.doCliente()))
                        .toList(),
                r.alocacoes().stream()
                        .map(a -> new AlocacaoResponse(a.usuarioId(), nome.apply(a.usuarioId()), a.atividadeId(),
                                a.data(), a.horas(), a.tipo()))
                        .toList(),
                r.conflitos().stream()
                        .map(c -> new ConflitoResponse(c.usuarioId(), nome.apply(c.usuarioId()), c.semana(), c.horas(),
                                c.mensagem()))
                        .toList());
    }

    private static BigDecimal zeroSeNull(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
