package br.com.vrinteriorpaulista.validator_infra.monitor;

import br.com.vrinteriorpaulista.validator_infra.monitor.dto.OrdemVersao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrdemVersaoTest {

    @Test
    void ordenaPartesNumericasComoNumero() {
        List<String> ordenadas = Stream.of("4.4.100", "4.4.93", "4.10.0", "4.4.9", "4.4", "10.0.0", "4.4.093")
                .sorted(OrdemVersao.COMPARADOR)
                .toList();
        assertThat(ordenadas).containsExactly("4.4", "4.4.9", "4.4.93", "4.4.093", "4.4.100", "4.10.0", "10.0.0");
    }

    @Test
    void preReleaseVemAntesDaVersaoFinal() {
        assertThat(OrdemVersao.comparar("2.5.0-rc.1", "2.5.0")).isNegative();
        assertThat(OrdemVersao.comparar("2.5.0-rc.2", "2.5.0-rc.10")).isNegative();
        assertThat(OrdemVersao.comparar("2.5.0", "2.4.9")).isPositive();
        assertThat(OrdemVersao.comparar("2.5.0", "2.5.0")).isZero();
    }

    @Test
    void numeroGrandeNaoEstoura() {
        assertThat(OrdemVersao.comparar("1.99999999999999999999", "1.100000000000000000000")).isNegative();
    }
}
