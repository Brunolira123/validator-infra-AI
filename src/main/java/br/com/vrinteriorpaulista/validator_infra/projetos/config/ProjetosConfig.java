package br.com.vrinteriorpaulista.validator_infra.projetos.config;

import br.com.vrinteriorpaulista.validator_infra.projetos.planejador.PlanejadorCronograma;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Módulo Projetos: planejamento de implantação (a execução segue no Artia). Tabelas no schema "projetos",
 * criadas pelo ddl-auto.
 */
@Configuration
public class ProjetosConfig {

    @Bean
    PlanejadorCronograma planejadorCronograma(@Value("${app.projetos.horas-dia}") BigDecimal horasDia) {
        return new PlanejadorCronograma(horasDia);
    }
}
