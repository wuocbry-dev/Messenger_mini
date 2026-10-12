package com.team503.messenger.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void corsAllowsConfiguredDevOriginWithCredentials() {
        SecurityConfig securityConfig = new SecurityConfig();
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(List.of("http://localhost:5173"));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/register");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "POST");

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
    }
}
