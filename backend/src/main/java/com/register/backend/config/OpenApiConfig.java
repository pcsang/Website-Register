package com.register.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger documentation metadata for the REST API.
 *
 * <p>Served by springdoc at {@code /v3/api-docs} (JSON) and the interactive UI at
 * {@code /swagger-ui.html}, generated automatically from controller and DTO annotations —
 * no per-endpoint wiring required here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Information Collection & Admin Management System API")
                        .description("Public submission form and admin management endpoints.")
                        .version("v1"));
    }
}
