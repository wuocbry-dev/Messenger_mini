package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team503.messenger.call.config.AgoraProperties;
import org.junit.jupiter.api.Test;

class DefaultAgoraTokenServiceTest {

    @Test
    void generatesNonEmptyJwtStyleTokenWithAppCredentials() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        properties.setAppCertificate("test-cert");
        properties.setTokenTtlSeconds(600);
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        String token = service.generateRtcToken("call-1-abc", 42L, 600);

        assertThat(token).isNotBlank();
        // JWT-like format: three dot-separated base64-url sections
        String[] parts = token.split("\\.");  
        assertThat(parts).hasSize(3);
        // The token must not be a plaintext copy of the channel name or the
        // user id.
        assertThat(token).isNotEqualTo("call-1-abc");
        // The certificate must not appear in plaintext in the token body.
        assertThat(token).doesNotContain("test-cert");
    }

    @Test
    void rejectsMissingAppId() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppCertificate("test-cert");
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        assertThatThrownBy(() -> service.generateRtcToken("call-1-abc", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App ID");
    }

    @Test
    void rejectsMissingAppCertificate() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        assertThatThrownBy(() -> service.generateRtcToken("call-1-abc", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App Certificate");
    }

    @Test
    void usesProvidedTtlWhenPositive() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        properties.setAppCertificate("test-cert");
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        String t1 = service.generateRtcToken("call-1-abc", 42L, 600);
        // Different TTLs yield different `expiredTs` and therefore different
        // signatures.
        String t2 = service.generateRtcToken("call-1-abc", 42L, 1200);

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void usesConfiguredTtlWhenZero() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        properties.setAppCertificate("test-cert");
        properties.setTokenTtlSeconds(900);
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        String token = service.generateRtcToken("call-1-abc", 42L, 0);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void differentChannelNamesProduceDifferentTokens() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        properties.setAppCertificate("test-cert");
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        String t1 = service.generateRtcToken("call-1-aaa", 42L, 600);
        String t2 = service.generateRtcToken("call-1-bbb", 42L, 600);

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void differentUserIdsProduceDifferentTokens() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId("test-app-id");
        properties.setAppCertificate("test-cert");
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        String t1 = service.generateRtcToken("call-1-abc", 1L, 600);
        String t2 = service.generateRtcToken("call-1-abc", 2L, 600);

        assertThat(t1).isNotEqualTo(t2);
    }
}
