package br.com.vrinteriorpaulista.validator_infra.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real e descartável para os testes de integração. O @ServiceConnection injeta URL, usuário e senha
 * no datasource; não há configuração manual de conexão.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * Estático para a suíte inteira usar um único container, mesmo quando classes de teste geram
     * contextos Spring diferentes (ex: uma com @MockitoBean e outra sem).
     */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:14-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
