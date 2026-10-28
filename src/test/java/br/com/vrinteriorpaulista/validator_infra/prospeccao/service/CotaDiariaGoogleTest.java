package br.com.vrinteriorpaulista.validator_infra.prospeccao.service;

import br.com.vrinteriorpaulista.validator_infra.exception.LimiteDiarioAtingidoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CotaDiariaGoogleTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Test
    void buscaAlemDoLimiteDa429() {
        CotaDiariaGoogle cota = new CotaDiariaGoogle(2, 1, Clock.fixed(Instant.parse("2026-10-28T13:00:00Z"), FUSO));
        cota.consumirBusca();
        cota.consumirBusca();

        assertThatThrownBy(cota::consumirBusca)
                .isInstanceOf(LimiteDiarioAtingidoException.class)
                .hasMessage(CotaDiariaGoogle.MENSAGEM_BUSCAS);
    }

    @Test
    void fotoAlemDoLimiteSoRecusa() {
        CotaDiariaGoogle cota = new CotaDiariaGoogle(1, 1, Clock.fixed(Instant.parse("2026-10-28T13:00:00Z"), FUSO));

        assertThat(cota.tentarConsumirFoto()).isTrue();
        assertThat(cota.tentarConsumirFoto()).isFalse();
    }

    @Test
    void zeraNaMeiaNoiteDeSaoPaulo() {
        // 23:30 em São Paulo (02:30 UTC do dia seguinte): o dia ainda não virou no fuso do Brasil
        RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-10-29T02:30:00Z"));
        CotaDiariaGoogle cota = new CotaDiariaGoogle(1, 1, relogio);
        cota.consumirBusca();
        assertThat(cota.tentarConsumirFoto()).isTrue();
        assertThatThrownBy(cota::consumirBusca).isInstanceOf(LimiteDiarioAtingidoException.class);

        relogio.avancar(Duration.ofMinutes(31));

        cota.consumirBusca();
        assertThat(cota.tentarConsumirFoto()).isTrue();
    }

    private static class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
