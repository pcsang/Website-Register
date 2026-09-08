package com.register.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the REST API.
 *
 * <p>Allows only explicitly configured frontend origins (never a blanket "*") to call the API
 * with the HTTP methods and headers the frontend actually needs. Allowed origins are read from
 * configuration ({@code app.cors.allowed-origins}, backed by the {@code ALLOWED_ORIGINS}
 * environment variable) so that new environments (e.g. a production frontend on Vercel) can be
 * allowed by changing configuration only, without a code change.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    /**
     * Creates the CORS configuration from the configured allowed-origins property.
     *
     * @param allowedOrigins comma-separated list of origins allowed to call the API, bound from
     *                        {@code app.cors.allowed-origins}
     */
    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    /**
     * Registers the global CORS mapping applied to every API endpoint.
     *
     * @param registry the Spring MVC CORS registry to configure
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
