package br.com.vrinteriorpaulista.validator_infra.service.vision;

import br.com.vrinteriorpaulista.validator_infra.dto.vision.AnaliseEquipamentoDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Só Mockito: nenhum teste chama a API do Google. */
class FallbackVisionProviderTest {

    private static final byte[] IMAGEM = {1, 2, 3};
    private static final String MIME = "image/jpeg";
    private static final String PRINCIPAL = "gemini-3.8-flash";
    private static final String SEGUNDO = "gemini-2.0-flash";
    private static final String TERCEIRO = "gemini-2.5-flash";

    private final OpenRouterVisionProvider cliente = mock(OpenRouterVisionProvider.class);
    private final FallbackVisionProvider provider =
            new FallbackVisionProvider(cliente, List.of(PRINCIPAL, SEGUNDO, TERCEIRO), 2);

    @Test
    void deveUsarSegundoModeloQuandoPrincipalFalha2x() {
        AnaliseEquipamentoDTO esperado = mock(AnaliseEquipamentoDTO.class);
        when(cliente.analisarImagem(IMAGEM, MIME, PRINCIPAL))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"));
        when(cliente.analisarImagem(IMAGEM, MIME, SEGUNDO)).thenReturn(esperado);

        AnaliseEquipamentoDTO resultado = provider.analisarImagem(IMAGEM, MIME);

        assertThat(resultado).isSameAs(esperado);
        InOrder ordem = inOrder(cliente);
        ordem.verify(cliente, times(2)).analisarImagem(IMAGEM, MIME, PRINCIPAL);
        ordem.verify(cliente).analisarImagem(IMAGEM, MIME, SEGUNDO);
        verify(cliente, never()).analisarImagem(any(), anyString(), eq(TERCEIRO));
    }

    @Test
    void deveLancarTransientAiExceptionQuandoTodosOsModelosFalham() {
        TransientAiException ultima = new TransientAiException("HTTP 429 - quota");
        when(cliente.analisarImagem(any(), anyString(), anyString()))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(new TransientAiException("HTTP 503 - high demand"))
                .thenThrow(ultima);

        assertThatThrownBy(() -> provider.analisarImagem(IMAGEM, MIME))
                .isInstanceOf(TransientAiException.class)
                .hasMessageContaining(PRINCIPAL)
                .hasMessageContaining(TERCEIRO)
                .hasCause(ultima);

        verify(cliente, times(2)).analisarImagem(IMAGEM, MIME, PRINCIPAL);
        verify(cliente, times(2)).analisarImagem(IMAGEM, MIME, SEGUNDO);
        verify(cliente, times(2)).analisarImagem(IMAGEM, MIME, TERCEIRO);
    }

    @Test
    void naoDeveTrocarDeModeloEmErroNaoTransitorio() {
        NonTransientAiException chaveInvalida = new NonTransientAiException("HTTP 401 - invalid key");
        when(cliente.analisarImagem(IMAGEM, MIME, PRINCIPAL)).thenThrow(chaveInvalida);

        assertThatThrownBy(() -> provider.analisarImagem(IMAGEM, MIME)).isSameAs(chaveInvalida);

        verify(cliente, times(1)).analisarImagem(IMAGEM, MIME, PRINCIPAL);
        verify(cliente, never()).analisarImagem(any(), anyString(), eq(SEGUNDO));
    }
}
