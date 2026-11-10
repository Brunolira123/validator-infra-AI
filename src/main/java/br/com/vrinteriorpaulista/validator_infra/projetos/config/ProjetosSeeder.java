package br.com.vrinteriorpaulista.validator_infra.projetos.config;

import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateAtividadeRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.dto.TemplateRequest;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Categoria;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.EscopoFeriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.entity.Feriado;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.CategoriaRepository;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.FeriadoRepository;
import br.com.vrinteriorpaulista.validator_infra.projetos.repository.TemplateRepository;
import br.com.vrinteriorpaulista.validator_infra.projetos.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dados iniciais do módulo Projetos. Categorias e templates só com a tabela vazia; feriados nacionais por ano,
 * só quando o ano ainda não tem nenhum (apagar um feriado de propósito não o traz de volta no próximo start).
 */
@Component
@Slf4j
public class ProjetosSeeder implements CommandLineRunner {

    static final int PRIMEIRO_ANO = 2026;
    static final int ULTIMO_ANO = 2028;

    private final CategoriaRepository categoriaRepo;
    private final FeriadoRepository feriadoRepo;
    private final TemplateRepository templateRepo;
    private final TemplateService templates;

    public ProjetosSeeder(CategoriaRepository categoriaRepo, FeriadoRepository feriadoRepo,
                          TemplateRepository templateRepo, TemplateService templates) {
        this.categoriaRepo = categoriaRepo;
        this.feriadoRepo = feriadoRepo;
        this.templateRepo = templateRepo;
        this.templates = templates;
    }

    @Override
    public void run(String... args) {
        if (categoriaRepo.count() == 0) {
            log.info("Populando categorias de atividades de implantação...");
            categoria("Kickoff", "2");
            categoria("Conversão de banco", "16");
            categoria("Conferência de dados", "8");
            categoria("Treinamento", "12");
            categoria("Implantação", "16");
        }
        for (int ano = PRIMEIRO_ANO; ano <= ULTIMO_ANO; ano++) {
            popularFeriados(ano);
        }
        if (templateRepo.count() == 0) {
            log.info("Populando templates de implantação...");
            popularTemplates();
        }
    }

    private void categoria(String nome, String horas) {
        Categoria c = new Categoria();
        c.setNome(nome);
        c.setHorasPadrao(new BigDecimal(horas));
        c.setAtivo(true);
        categoriaRepo.save(c);
    }

    private void popularFeriados(int ano) {
        Map<LocalDate, String> nacionais = FeriadosNacionais.doAno(ano);
        boolean anoJaTem = nacionais.keySet().stream()
                .anyMatch(d -> feriadoRepo.existsByDataAndEscopo(d, EscopoFeriado.NACIONAL));
        if (anoJaTem) return;
        log.info("Populando feriados nacionais de {}...", ano);
        nacionais.forEach((data, descricao) -> {
            Feriado f = new Feriado();
            f.setData(data);
            f.setDescricao(descricao);
            f.setEscopo(EscopoFeriado.NACIONAL);
            feriadoRepo.save(f);
        });
    }

    /**
     * Os dois templates do Fluxo. As atividades principais viraram resumo (regra da 0.4: sem horas próprias) e as
     * dependências seguem o deslocamento de dias do Fluxo.
     */
    private void popularTemplates() {
        templates.criar(new TemplateRequest("Nova loja", true, List.of(
                atividade("preparar", null, "Preparar nova loja", null, List.of()),
                atividade("cadastro", "preparar", "Receber cadastro de produtos", "4", List.of()),
                atividade("equipamentos", "preparar", "Conferir equipamentos", "3", List.of("cadastro")),
                atividade("treinar", null, "Treinar equipe", "8", List.of("preparar")))));
        templates.criar(new TemplateRequest("Implantação Master Fisco", true, List.of(
                atividade("implantar", null, "Implantar Master Fisco", null, List.of()),
                atividade("levantar", "implantar", "Levantar dados fiscais", "4", List.of()),
                atividade("configurar", "implantar", "Configurar parâmetros fiscais", "6", List.of("levantar")),
                atividade("validar", null, "Validar emissão com o cliente", "4", List.of("implantar")))));
    }

    private static TemplateAtividadeRequest atividade(String chave, String pai, String nome, String horas,
                                                      List<String> dependeDe) {
        return new TemplateAtividadeRequest(chave, pai, nome, null, horas != null ? new BigDecimal(horas) : BigDecimal.ZERO,
                dependeDe, false, false, 1, null);
    }
}
