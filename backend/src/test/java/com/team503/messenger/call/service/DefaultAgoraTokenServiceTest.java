package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team503.messenger.call.config.AgoraProperties;
import org.junit.jupiter.api.Test;

class DefaultAgoraTokenServiceTest {

    // Test vector from Agora's reference builder:
    // AgoraIO/Tools DynamicKey/AgoraDynamicKey/java/src/test/java/io/agora/media/AccessToken2Test.java
    private static final String REF_APP_ID = "970CA35de60c44645bbae8a215061b33";
    private static final String REF_APP_CERT = "5CFd2fd1755d40ecb72977518be15d3b";
    private static final String REF_CHANNEL = "7d72365eb983485397e3e3f9d460bdda";

    private static AgoraProperties validProperties() {
        AgoraProperties properties = new AgoraProperties();
        properties.setAppId(REF_APP_ID);
        properties.setAppCertificate(REF_APP_CERT);
        properties.setTokenTtlSeconds(600);
        return properties;
    }

    @Test
    void matchesAgoraReferenceVectorForUid() {
        String token = DefaultAgoraTokenService.buildToken(
                REF_APP_ID, REF_APP_CERT, REF_CHANNEL, "2882341273", 600, 1111111, 1,
                new short[] {DefaultAgoraTokenService.PRIVILEGE_JOIN_CHANNEL}
        );

        assertThat(token).isEqualTo(
                "007eJxTYBBbsMMnKq7p9Hf/HcIX5kce9b518kCiQgSr5Zrp4X1Tu6UUGCzNDZwdjU1TUs0Mkk1MzExMk5ISUy0SjQxNDcwMk4yN3b8IMEQwMTAwMoAwBIL4CgzmKeZGxmamqUmWFsYmFqbGluapxqnGaZYpJmYGSSkpiVwMRhYWRsYmhkbmxgDCaiTj");
    }

    @Test
    void matchesAgoraReferenceVectorForUidZero() {
        String token = DefaultAgoraTokenService.buildToken(
                REF_APP_ID, REF_APP_CERT, REF_CHANNEL, "", 600, 1111111, 1,
                new short[] {DefaultAgoraTokenService.PRIVILEGE_JOIN_CHANNEL}
        );

        assertThat(token).isEqualTo(
                "007eJxTYLhzZP08Lxa1Pg57+TcXb/3cZ3wi4V6kbpbOog0G2dOYk20UGCzNDZwdjU1TUs0Mkk1MzExMk5ISUy0SjQxNDcwMk4yN3b8IMEQwMTAwMoAwBIL4CgzmKeZGxmamqUmWFsYmFqbGluapxqnGaZYpJmYGSSkpiQwMADacImo=");
    }

    @Test
    void uidIsEncodedAsUnsignedDecimalAndZeroAsEmpty() {
        assertThat(DefaultAgoraTokenService.uidString(19L)).isEqualTo("19");
        assertThat(DefaultAgoraTokenService.uidString(2882341273L)).isEqualTo("2882341273");
        assertThat(DefaultAgoraTokenService.uidString(0L)).isEmpty();
    }

    @Test
    void generatesVersion007TokenWithoutLeakingCertificate() {
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(validProperties());

        String token = service.generateRtcToken("call-1-abc", 42L, 600);

        assertThat(token).startsWith("007");
        assertThat(token).doesNotContain(REF_APP_CERT);
    }

    @Test
    void rejectsMissingAppId() {
        AgoraProperties properties = validProperties();
        properties.setAppId(null);
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        assertThatThrownBy(() -> service.generateRtcToken("call-1-abc", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App ID");
    }

    @Test
    void rejectsMissingAppCertificate() {
        AgoraProperties properties = validProperties();
        properties.setAppCertificate(null);
        DefaultAgoraTokenService service = new DefaultAgoraTokenService(properties);

        assertThatThrownBy(() -> service.generateRtcToken("call-1-abc", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App Certificate");
    }

    @Test
    void differentChannelsAndUsersProduceDifferentTokens() {
        String base = DefaultAgoraTokenService.buildToken(
                REF_APP_ID, REF_APP_CERT, "call-1-aaa", "1", 600, 1111111, 1,
                new short[] {DefaultAgoraTokenService.PRIVILEGE_JOIN_CHANNEL});
        String otherChannel = DefaultAgoraTokenService.buildToken(
                REF_APP_ID, REF_APP_CERT, "call-1-bbb", "1", 600, 1111111, 1,
                new short[] {DefaultAgoraTokenService.PRIVILEGE_JOIN_CHANNEL});
        String otherUser = DefaultAgoraTokenService.buildToken(
                REF_APP_ID, REF_APP_CERT, "call-1-aaa", "2", 600, 1111111, 1,
                new short[] {DefaultAgoraTokenService.PRIVILEGE_JOIN_CHANNEL});

        assertThat(base).isNotEqualTo(otherChannel).isNotEqualTo(otherUser);
    }
}
