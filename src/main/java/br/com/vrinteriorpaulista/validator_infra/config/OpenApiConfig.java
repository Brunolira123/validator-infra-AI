package br.com.vrinteriorpaulista.validator_infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Validador de Infraestrutura VR")
                        .version("1.0.0-MVP")
                        .description("API para levantamento de infraestrutura de clientes VR: cadastro de clientes, "
                                + "levantamentos, upload de fotos dos equipamentos, extração de especificações por IA "
                                + "e validação contra os requisitos e a base de equipamentos homologados."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acesso obtido em POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }
}
