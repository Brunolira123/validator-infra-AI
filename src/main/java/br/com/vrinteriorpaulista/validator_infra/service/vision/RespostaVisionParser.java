package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

/**
 * Converte a resposta textual da IA em AnaliseEquipamentoDTO.
 * <p>
 * Modelos de raciocínio costumam escrever texto antes (e às vezes depois) do JSON, inclusive com chaves
 * ou exemplos de JSON no meio do raciocínio. Por isso cada '{' da resposta é testado como início de um
 * objeto, com as chaves casadas, e vale o último objeto válido que tenha algum campo exclusivo da raiz do schema.
 * <p>
 * Nunca lança exceção: sem JSON utilizável, devolve um DTO com {@code erro} preenchido, que o motor de
 * regras trata como REQUER_ANALISE.
 */
@Slf4j
public class RespostaVisionParser {

    static final int TAMANHO_TRECHO_ERRO = 200;
    private static final int TAMANHO_TRECHO_LOG = 1000;
    /**
     * Campos que só existem na raiz do schema. "fabricante" e "modelo" ficam de fora porque também aparecem
     * dentro de "cpu": com eles, um JSON truncado teria o objeto da CPU aceito como se fosse a resposta.
     */
    private static final Set<String> CAMPOS_DA_RAIZ = Set.of(
            "cpu", "memoria", "armazenamento", "sistema_operacional",
            "confianca_global", "campos_inconclusivos", "observacoes", "erro"
    );

    private final ObjectMapper objectMapper;

    public RespostaVisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnaliseEquipamentoDTO interpretar(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            log.warn("Resposta da IA vazia");
            return comErro("Resposta vazia da IA");
        }

        String json = extrairJson(resposta);
        if (json == null) {
            log.warn("Resposta da IA sem JSON utilizável: {}", truncar(resposta, TAMANHO_TRECHO_LOG));
            return comErro(trecho(resposta));
        }

        try {
            return objectMapper.readValue(json, AnaliseEquipamentoDTO.class);
        } catch (JacksonException e) {
            log.warn("JSON da IA fora do formato esperado ({}): {}", e.getOriginalMessage(), truncar(json, TAMANHO_TRECHO_LOG));
            return comErro(trecho(resposta));
        }
    }

    /**
     * Último objeto JSON válido da resposta com algum campo exclusivo da raiz do schema, ou null se não houver.
     */
    String extrairJson(String resposta) {
        String encontrado = null;
        int inicio = resposta.indexOf('{');

        while (inicio >= 0) {
            int fim = fechamento(resposta, inicio);
            if (fim < 0) {
                inicio = resposta.indexOf('{', inicio + 1);
                continue;
            }
            String candidato = resposta.substring(inicio, fim + 1);
            if (isObjetoDoSchema(candidato)) {
                encontrado = candidato;
                inicio = resposta.indexOf('{', fim + 1);
            } else {
                inicio = resposta.indexOf('{', inicio + 1);
            }
        }
        return encontrado;
    }

    /**
     * Posição do '}' que fecha o '{' em {@code inicio}, ignorando chaves dentro de strings; -1 se não fechar.
     */
    private int fechamento(String texto, int inicio) {
        int profundidade = 0;
        boolean emString = false;
        boolean escapado = false;

        for (int i = inicio; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (emString) {
                if (escapado) {
                    escapado = false;
                } else if (c == '\\') {
                    escapado = true;
                } else if (c == '"') {
                    emString = false;
                }
            } else if (c == '"') {
                emString = true;
            } else if (c == '{') {
                profundidade++;
            } else if (c == '}') {
                profundidade--;
                if (profundidade == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean isObjetoDoSchema(String candidato) {
        try {
            JsonNode no = objectMapper.readTree(candidato);
            return no.isObject() && no.propertyNames().stream().anyMatch(CAMPOS_DA_RAIZ::contains);
        } catch (JacksonException e) {
            return false;
        }
    }

    private AnaliseEquipamentoDTO comErro(String erro) {
        return new AnaliseEquipamentoDTO(null, null, null, null, null, null, null, List.of(), null, erro);
    }

    private String trecho(String resposta) {
        return truncar(resposta.strip().replaceAll("\\s+", " "), TAMANHO_TRECHO_ERRO);
    }

    private String truncar(String texto, int tamanho) {
        return texto.length() <= tamanho ? texto : texto.substring(0, tamanho);
    }
}
