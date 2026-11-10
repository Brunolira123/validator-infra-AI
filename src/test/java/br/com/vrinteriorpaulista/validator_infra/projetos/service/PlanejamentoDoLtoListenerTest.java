package br.com.vrinteriorpaulista.validator_infra.projetos.service;

import br.com.vrinteriorpaulista.validator_infra.prospeccao.service.LtoRealizadoEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlanejamentoDoLtoListenerTest {

    private final PlanejamentoService planejamentos = mock(PlanejamentoService.class);
    private final PlanejamentoDoLtoListener listener = new PlanejamentoDoLtoListener(planejamentos);

    @Test
    void criaORascunhoDoClienteDoLto() {
        listener.aoRealizarLto(new LtoRealizadoEvent(1L, 2L, 3L));

        verify(planejamentos).criarRascunhoDoLto(2L, 3L);
    }

    @Test
    void falhaAoCriarOPlanejamentoNaoChegaAoLto() {
        doThrow(new IllegalStateException("banco fora")).when(planejamentos).criarRascunhoDoLto(2L, 3L);

        assertThatCode(() -> listener.aoRealizarLto(new LtoRealizadoEvent(1L, 2L, 3L))).doesNotThrowAnyException();
    }
}
