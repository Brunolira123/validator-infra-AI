package br.com.vrinteriorpaulista.validator_infra.service;

import br.com.vrinteriorpaulista.validator_infra.dto.response.ResultadoAnaliseDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.ArmazenamentoExtraido;
import br.com.vrinteriorpaulista.validator_infra.dto.vision.CampoExtraido;
import br.com.vrinteriorpaulista.validator_infra.entity.EquipamentoHomologado;
import br.com.vrinteriorpaulista.validator_infra.entity.Requisito;
import br.com.vrinteriorpaulista.validator_infra.enums.CategoriaEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.FuncaoEquipamento;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusAnalise;
import br.com.vrinteriorpaulista.validator_infra.enums.StatusHomologacao;
import br.com.vrinteriorpaulista.validator_infra.repository.EquipamentoHomologadoRepository;
import br.com.vrinteriorpaulista.validator_infra.repository.RequisitoRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MotorRegrasService {

    private static final String CAMPO_ARMAZENAMENTO_TIPO = "armazenamento_tipo";
    private static final String CAMPO_ARMAZENAMENTO_GB = "armazenamento_gb";

    private static final String CAMPO_HOMOLOGACAO = "homologacao";
    private static final Pattern SUFIXOS_IGNORADOS =
            Pattern.compile("\\b(sff|mt|tower|torre|gen\\s*\\d*|g(?:10|[1-9]))\\b");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private final RequisitoRepository requisitoRepo;
    private final EquipamentoHomologadoRepository homologadoRepo;

    public MotorRegrasService(RequisitoRepository requisitoRepo,
                              EquipamentoHomologadoRepository homologadoRepo) {
        this.requisitoRepo = requisitoRepo;
        this.homologadoRepo = homologadoRepo;
    }

    public ResultadoAnaliseDTO avaliar(AnaliseEquipamentoDTO dados,
                                       CategoriaEquipamento categoria,
                                       FuncaoEquipamento funcao) {

        if (dados.erro() != null) {
            return new ResultadoAnaliseDTO(
                    StatusAnalise.REQUER_ANALISE,
                    List.of(),
                    "IA não reconheceu a imagem: " + dados.erro()
            );
        }

        List<ResultadoAnaliseDTO.ItemAvaliadoDTO> itens = new ArrayList<>();
        boolean temNaoAtende = false;
        boolean temRequerAnalise = false;

        ResultadoAnaliseDTO.ItemAvaliadoDTO homologacao = avaliarHomologacao(dados);
        if (homologacao != null) {
            itens.add(homologacao);
            if (homologacao.status() == StatusAnalise.NAO_ATENDE) temNaoAtende = true;
            if (homologacao.status() == StatusAnalise.REQUER_ANALISE) temRequerAnalise = true;
        }

        List<Requisito> requisitos = requisitoRepo
                .findByCategoriaAndFuncaoAndVigenteTrue(categoria, funcao);

        if (requisitos.isEmpty()) {
            return new ResultadoAnaliseDTO(
                    temNaoAtende ? StatusAnalise.NAO_ATENDE : StatusAnalise.REQUER_ANALISE,
                    itens,
                    "Nenhum requisito cadastrado para esta categoria/função"
            );
        }

        Requisito requisitoTipoDisco = requisitos.stream()
                .filter(r -> CAMPO_ARMAZENAMENTO_TIPO.equals(r.getCampo()))
                .findFirst()
                .orElse(null);

        for (Requisito req : requisitos) {
            ItemAvaliacao item = avaliarRequisito(req, dados, requisitoTipoDisco);

            itens.add(new ResultadoAnaliseDTO.ItemAvaliadoDTO(
                    req.getCampo(),
                    item.valorEncontrado(),
                    req.getOperador() + " " + req.getValor(),
                    item.status(),
                    item.observacao()
            ));

            if (item.status() == StatusAnalise.NAO_ATENDE) temNaoAtende = true;
            if (item.status() == StatusAnalise.REQUER_ANALISE) temRequerAnalise = true;
        }

        StatusAnalise resultadoFinal;
        String justificativa;

        if (temNaoAtende) {
            resultadoFinal = StatusAnalise.NAO_ATENDE;
            justificativa = "Um ou mais requisitos não foram atendidos.";
        } else if (temRequerAnalise) {
            resultadoFinal = StatusAnalise.REQUER_ANALISE;
            justificativa = "Um ou mais requisitos precisam de análise manual.";
        } else {
            resultadoFinal = StatusAnalise.ATENDE;
            justificativa = "Todos os requisitos foram atendidos.";
        }

        return new ResultadoAnaliseDTO(resultadoFinal, itens, justificativa);
    }

    /**
     * Retorna null quando a IA não identificou fabricante ou modelo (checagem não se aplica).
     */
    private ResultadoAnaliseDTO.ItemAvaliadoDTO avaliarHomologacao(AnaliseEquipamentoDTO dados) {
        String fabricante = valorTexto(dados.fabricante());
        String modelo = valorTexto(dados.modelo());
        if (fabricante == null || modelo == null) {
            return null;
        }

        String equipamento = fabricante + " " + modelo;
        String fabricanteBusca = normalizarParaBusca(fabricante);
        String modeloBusca = normalizarParaBusca(modelo);

        List<EquipamentoHomologado> encontrados = List.of();
        if (!fabricanteBusca.isEmpty() && !modeloBusca.isEmpty()) {
            encontrados = homologadoRepo
                    .findByFabricanteIgnoreCaseAndModeloIgnoreCase(fabricanteBusca, modeloBusca);
            if (encontrados.isEmpty()) {
                encontrados = homologadoRepo
                        .findByFabricanteIgnoreCaseAndModeloContainingIgnoreCase(fabricanteBusca, modeloBusca);
            }
        }

        if (encontrados.isEmpty()) {
            return itemHomologacao(equipamento, StatusAnalise.REQUER_ANALISE,
                    "Equipamento não consta na base de homologados");
        }

        long statusDistintos = encontrados.stream().map(EquipamentoHomologado::getStatus).distinct().count();
        if (statusDistintos > 1) {
            String candidatos = encontrados.stream()
                    .map(h -> h.getModelo() + " (" + h.getStatus() + ")")
                    .collect(Collectors.joining(", "));
            return itemHomologacao(equipamento, StatusAnalise.REQUER_ANALISE,
                    "Correspondência ambígua na base de homologados: " + candidatos);
        }

        return switch (encontrados.get(0).getStatus()) {
            case HOMOLOGADO -> itemHomologacao(equipamento, StatusAnalise.ATENDE,
                    "Equipamento homologado");
            case NAO_HOMOLOGADO -> itemHomologacao(equipamento, StatusAnalise.NAO_ATENDE,
                    "Equipamento marcado como NÃO homologado");
            case EM_AVALIACAO -> itemHomologacao(equipamento, StatusAnalise.REQUER_ANALISE,
                    "Equipamento em avaliação de homologação");
            case DESCONTINUADO -> itemHomologacao(equipamento, StatusAnalise.REQUER_ANALISE,
                    "Equipamento descontinuado, avaliar substituto");
        };
    }

    private ResultadoAnaliseDTO.ItemAvaliadoDTO itemHomologacao(String equipamento,
                                                                StatusAnalise status,
                                                                String observacao) {
        return new ResultadoAnaliseDTO.ItemAvaliadoDTO(
                CAMPO_HOMOLOGACAO, equipamento, StatusHomologacao.HOMOLOGADO.name(), status, observacao);
    }

    /**
     * Lowercase, remove sufixos de formato/geração que variam entre a IA e o cadastro
     * (SFF, MT, Tower, Torre, Gen/GenN, G1..G10) e colapsa espaços.
     */
    private String normalizarParaBusca(String valor) {
        String semSufixos = SUFIXOS_IGNORADOS.matcher(valor.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return ESPACOS.matcher(semSufixos).replaceAll(" ").trim();
    }

    private String valorTexto(CampoExtraido<String> campo) {
        if (campo == null || campo.valor() == null || campo.valor().isBlank()) return null;
        return campo.valor().trim();
    }

    private ItemAvaliacao avaliarRequisito(Requisito req, AnaliseEquipamentoDTO dados, Requisito requisitoTipoDisco) {
        return switch (req.getCampo()) {
            case CAMPO_ARMAZENAMENTO_TIPO -> avaliarArmazenamento(req, dados, null);
            case CAMPO_ARMAZENAMENTO_GB -> avaliarArmazenamento(req, dados, requisitoTipoDisco);
            default -> avaliarCampo(extrairCampo(req.getCampo(), dados), req);
        };
    }

    /**
     * Avalia o requisito contra todos os discos: basta um disco atender.
     * Para capacidade, se houver requisito de tipo, o disco também precisa ser do tipo exigido
     * (ex: existe pelo menos um SSD >= 480GB).
     */
    private ItemAvaliacao avaliarArmazenamento(Requisito req, AnaliseEquipamentoDTO dados, Requisito requisitoTipo) {
        List<ArmazenamentoExtraido> discos = dados.armazenamento() == null
                ? List.of()
                : dados.armazenamento().stream().filter(Objects::nonNull).toList();

        if (discos.isEmpty()) {
            return naoIdentificado();
        }

        List<ItemAvaliacao> porDisco = discos.stream()
                .map(disco -> avaliarDisco(disco, req, requisitoTipo))
                .toList();

        Optional<ItemAvaliacao> atende = porDisco.stream()
                .filter(i -> i.status() == StatusAnalise.ATENDE)
                .findFirst();
        if (atende.isPresent()) {
            return atende.get();
        }

        Optional<ItemAvaliacao> requerAnalise = porDisco.stream()
                .filter(i -> i.status() == StatusAnalise.REQUER_ANALISE)
                .findFirst();
        if (requerAnalise.isPresent()) {
            return requerAnalise.get();
        }

        if (porDisco.size() == 1) {
            return porDisco.get(0);
        }

        String valores = porDisco.stream()
                .map(ItemAvaliacao::valorEncontrado)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return new ItemAvaliacao(valores, StatusAnalise.NAO_ATENDE, "Nenhum disco atende ao requisito.");
    }

    private ItemAvaliacao avaliarDisco(ArmazenamentoExtraido disco, Requisito req, Requisito requisitoTipo) {
        if (CAMPO_ARMAZENAMENTO_TIPO.equals(req.getCampo())) {
            return avaliarCampo(disco.tipo(), req);
        }

        ItemAvaliacao capacidade = avaliarCampo(disco.capacidade_gb(), req);
        if (requisitoTipo == null || capacidade.status() != StatusAnalise.ATENDE) {
            return capacidade;
        }

        ItemAvaliacao tipo = avaliarCampo(disco.tipo(), requisitoTipo);
        if (tipo.status() == StatusAnalise.ATENDE) {
            return capacidade;
        }

        String observacao = tipo.status() == StatusAnalise.NAO_ATENDE
                ? "Capacidade atende, mas o disco não é do tipo exigido (" + requisitoTipo.getValor() + ")."
                : "Capacidade atende, mas o tipo do disco é inconclusivo: " + tipo.observacao();
        return new ItemAvaliacao(capacidade.valorEncontrado(), tipo.status(), observacao);
    }

    private ItemAvaliacao avaliarCampo(CampoExtraido<?> campo, Requisito req) {
        if (campo == null || campo.valor() == null) {
            return naoIdentificado();
        }

        String valor = String.valueOf(campo.valor());

        if (!campo.isConclusivo()) {
            String observacao = campo.confianca() == null
                    ? "Confiança não informada na identificação."
                    : "Confiança baixa (" + Math.round(campo.confianca() * 100) + "%) na identificação.";
            return new ItemAvaliacao(valor, StatusAnalise.REQUER_ANALISE, observacao);
        }

        Optional<Boolean> atende = comparar(valor, req.getOperador(), req.getValor());

        if (atende.isEmpty()) {
            return new ItemAvaliacao(
                    valor,
                    StatusAnalise.REQUER_ANALISE,
                    "Valor não numérico ou mal formatado: " + valor
            );
        }

        return new ItemAvaliacao(
                valor,
                atende.get() ? StatusAnalise.ATENDE : StatusAnalise.NAO_ATENDE,
                atende.get() ? "OK" : "Valor fora do requisito mínimo."
        );
    }

    private ItemAvaliacao naoIdentificado() {
        return new ItemAvaliacao(
                null,
                StatusAnalise.REQUER_ANALISE,
                "Campo não identificado pela IA. Necessário análise manual."
        );
    }

    private CampoExtraido<?> extrairCampo(String campo, AnaliseEquipamentoDTO d) {
        return switch (campo) {
            case "ram_gb" -> d.memoria() != null ? d.memoria().total_gb() : null;
            case "cpu_cores" -> d.cpu() != null ? d.cpu().cores() : null;
            case "cpu_threads" -> d.cpu() != null ? d.cpu().threads() : null;
            case "cpu_geracao" -> d.cpu() != null ? d.cpu().geracao() : null;
            case "cpu_modelo" -> d.cpu() != null ? d.cpu().modelo() : null;
            case "cpu_fabricante" -> d.cpu() != null ? d.cpu().fabricante() : null;
            case "fabricante" -> d.fabricante();
            case "modelo" -> d.modelo();
            case "so_nome" -> d.sistema_operacional() != null ? d.sistema_operacional().nome() : null;
            case "so_versao" -> d.sistema_operacional() != null ? d.sistema_operacional().versao() : null;
            default -> null;
        };
    }

    /**
     * Retorna vazio quando a comparação numérica não é possível (valor mal formatado).
     */
    private Optional<Boolean> comparar(String valorEncontrado, String operador, String valorRequisito) {
        String valor = valorEncontrado.trim();

        return switch (operador) {
            case ">=" -> compararNumerico(valor, valorRequisito).map(c -> c >= 0);
            case "<=" -> compararNumerico(valor, valorRequisito).map(c -> c <= 0);
            case "==" -> Optional.of(valor.equalsIgnoreCase(valorRequisito.trim()));
            case "IN" -> Optional.of(Arrays.stream(valorRequisito.split("\\|"))
                    .map(String::trim)
                    .anyMatch(valor::equalsIgnoreCase));
            case "MATCHES" -> Optional.of(valor.matches("(?i).*(" + valorRequisito + ").*"));
            default -> Optional.of(false);
        };
    }

    private Optional<Integer> compararNumerico(String valorEncontrado, String valorRequisito) {
        try {
            double a = Double.parseDouble(valorEncontrado);
            double b = Double.parseDouble(valorRequisito.trim());
            return Optional.of(Double.compare(a, b));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record ItemAvaliacao(String valorEncontrado,
                                 StatusAnalise status,
                                 String observacao) {}
}
