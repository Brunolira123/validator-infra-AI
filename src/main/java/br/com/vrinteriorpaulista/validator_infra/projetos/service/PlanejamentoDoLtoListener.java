package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.service.LtoRealizadoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cria o planejamento RASCUNHO do cliente quando o LTO cria o Levantamento. Roda depois do commit do LTO e engole
 * qualquer falha: o LTO já está gravado e não pode ser desfeito por causa do planejamento.
 */
@Component
@Slf4j
public class PlanejamentoDoLtoListener {

    private final PlanejamentoService planejamentos;

    public PlanejamentoDoLtoListener(PlanejamentoService planejamentos) {
        this.planejamentos = planejamentos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRealizarLto(LtoRealizadoEvent evento) {
        try {
            planejamentos.criarRascunhoDoLto(evento.clienteId(), evento.levantamentoId());
        } catch (RuntimeException e) {
            log.error("LTO do prospect {} gravado, mas o planejamento do cliente {} (levantamento {}) não foi criado",
                    evento.prospectId(), evento.clienteId(), evento.levantamentoId(), e);
        }
    }
}
