package com.example.qldetai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class CorsConfigurationTests {
    private org.springframework.web.cors.CorsConfiguration configuration(String origins) {
        return new SecurityConfig(mock(ObjectMapper.class), origins, "lax", false, "")
                .corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/auth/csrf"));
    }

    @Test
    void localOriginsDoNotImplicitlyTrustCloudflareTunnels() {
        var cors = configuration("http://localhost:5173,http://127.0.0.1:5173");
        assertThat(cors.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(cors.checkOrigin("https://untrusted.trycloudflare.com")).isNull();
        assertThat(cors.checkOrigin("https://example.com")).isNull();
    }

    @Test
    void tunnelPatternsWorkOnlyWhenExplicitlyConfigured() {
        var cors = configuration("https://*.trycloudflare.com");
        assertThat(cors.checkOrigin("https://configured.trycloudflare.com")).isEqualTo("https://configured.trycloudflare.com");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.checkOrigin("https://example.com")).isNull();
    }
}
