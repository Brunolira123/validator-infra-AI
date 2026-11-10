package br.com.vrinteriorpaulista.validator_infra.projetos.entity;

import java.util.Map;
import java.util.Set;

/**
 * RASCUNHO (criado pelo LTO ou avulso) → EM_PLANEJAMENTO (template aplicado) → ENVIADO (ao cliente)
 * → CRIADO_ARTIA (a execução segue no Artia). CANCELADO pode ser reaberto como EM_PLANEJAMENTO.
 */
public enum SituacaoPlanejamento {
    RASCUNHO,
    EM_PLANEJAMENTO,
    ENVIADO,
    CRIADO_ARTIA,
    CANCELADO;

    private static final Map<SituacaoPlanejamento, Set<SituacaoPlanejamento>> TRANSICOES = Map.of(
            RASCUNHO, Set.of(EM_PLANEJAMENTO, CANCELADO),
            EM_PLANEJAMENTO, Set.of(ENVIADO, CANCELADO),
            ENVIADO, Set.of(EM_PLANEJAMENTO, CRIADO_ARTIA, CANCELADO),
            CRIADO_ARTIA, Set.of(CANCELADO),
            CANCELADO, Set.of(EM_PLANEJAMENTO));

    /** Situações cujo último cálculo ocupa a agenda dos analistas (capacidade entre projetos e carga). */
    public static final Set<SituacaoPlanejamento> OCUPAM_AGENDA = Set.of(EM_PLANEJAMENTO, ENVIADO, CRIADO_ARTIA);

    public boolean podeIrPara(SituacaoPlanejamento destino) {
        return TRANSICOES.get(this).contains(destino);
    }

    /** CRIADO_ARTIA e CANCELADO são somente leitura. */
    public boolean editavel() {
        return this == RASCUNHO || this == EM_PLANEJAMENTO || this == ENVIADO;
    }
}
