package br.com.vrinteriorpaulista.validator_infra.monitor;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Módulo de monitoramento (portal VRIP): recebe os resultados dos agentes instalados nos clientes.
 * Tabelas no schema "monitor", criadas pelo ddl-auto (hibernate.hbm2ddl.create_namespaces cria o schema).
 */
@Configuration
@EnableScheduling
public class MonitorConfig {
}
