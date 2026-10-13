package com.team503.messenger.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger configuration for Messenger Mini.
 *
 * Exposes:
 *   - API metadata (title, version, description, contact, license)
 *   - JWT Bearer auth scheme (so protected endpoints show an "Authorize" lock)
 *   - A configurable public base URL in the Servers dropdown
 *     (useful when the API is reached through a dev tunnel).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Bean
    public OpenAPI messengerMiniOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Messenger Mini API")
                        .version("v1")
                        .description("""
                                REST + WebSocket API for Messenger Mini.

                                Features: authentication, profiles, conversations,
                                messages, presence, typing/receipt events, file
                                attachments on Cloudflare R2, and Agora-backed
                                1-to-1 voice calls.
                                """)
                        .contact(new Contact()
                                .name("Messenger Mini Team")
                                .email("dev@team503.local"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://team503.local/license")))
                .servers(List.of(
                        new Server()
                                .url(publicBaseUrl)
                                .description("Current environment")
                ))
                // Make every operation require a Bearer token by default.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Paste a JWT access token from /api/v1/auth/login")));
    }
}
