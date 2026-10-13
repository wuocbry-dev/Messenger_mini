package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team503.messenger.call.config.AgoraProperties;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.Inflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class DefaultAgoraTokenServiceTest {

    private static final String APP_ID = "0123456789abcdef0123456789abcdef";
    private static final String CERT = "fedcba9876543210fedcba9876543210";
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private static DefaultAgoraTokenService service(String appId, String cert) {
        AgoraProperties p = new AgoraProperties();
        p.setAppId(appId);
        p.setAppCertificate(cert);
        p.setTokenTtlSeconds(900);
        return new DefaultAgoraTokenService(p, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void producesAccessToken2WithValidStructureAndSignature() throws Exception {
        String token = service(APP_ID, CERT).generateRtcToken("call-1-abc", 42L, 600);

        assertThat(token).startsWith("007");
        assertThat(token).doesNotContain(CERT);

        ByteBuffer buf = ByteBuffer.wrap(inflate(Base64.getDecoder().decode(token.substring(3))))
                .order(ByteOrder.LITTLE_ENDIAN);
        byte[] signature = readBytes(buf);
        byte[] signingInfo = Arrays.copyOfRange(buf.array(), buf.position(), buf.limit());

        assertThat(readString(buf)).isEqualTo(APP_ID);
        int issueTs = buf.getInt();
        assertThat(issueTs).isEqualTo((int) NOW.getEpochSecond());
        assertThat(buf.getInt()).isEqualTo(600);
        int salt = buf.getInt();
        assertThat(buf.getShort()).isEqualTo((short) 1);

        assertThat(buf.getShort()).isEqualTo((short) 1);   // RTC service
        assertThat(buf.getShort()).isEqualTo((short) 3);   // three privileges
        assertThat(buf.getShort()).isEqualTo((short) 1);   // join channel
        assertThat(buf.getInt()).isEqualTo(600);
        assertThat(buf.getShort()).isEqualTo((short) 2);   // publish audio
        assertThat(buf.getInt()).isEqualTo(600);
        assertThat(buf.getShort()).isEqualTo((short) 3);   // publish video
        assertThat(buf.getInt()).isEqualTo(600);
        assertThat(readString(buf)).isEqualTo("call-1-abc");
        assertThat(readString(buf)).isEqualTo("42");
        assertThat(buf.hasRemaining()).isFalse();

        byte[] key = hmac(le(issueTs), CERT.getBytes(StandardCharsets.UTF_8));
        key = hmac(le(salt), key);
        assertThat(signature).isEqualTo(hmac(key, signingInfo));
    }

    @Test
    void usesConfiguredTtlWhenZero() throws Exception {
        String token = service(APP_ID, CERT).generateRtcToken("c", 1L, 0);

        ByteBuffer buf = ByteBuffer.wrap(inflate(Base64.getDecoder().decode(token.substring(3))))
                .order(ByteOrder.LITTLE_ENDIAN);
        readBytes(buf);
        readString(buf);
        buf.getInt();
        assertThat(buf.getInt()).isEqualTo(900);
    }

    @Test
    void differentUsersAndChannelsProduceDifferentTokens() {
        DefaultAgoraTokenService s = service(APP_ID, CERT);
        assertThat(s.generateRtcToken("a", 1L, 600)).isNotEqualTo(s.generateRtcToken("a", 2L, 600));
        assertThat(s.generateRtcToken("a", 1L, 600)).isNotEqualTo(s.generateRtcToken("b", 1L, 600));
    }

    @Test
    void rejectsMissingAppId() {
        assertThatThrownBy(() -> service("", CERT).generateRtcToken("c", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App ID");
    }

    @Test
    void rejectsMissingAppCertificate() {
        assertThatThrownBy(() -> service(APP_ID, null).generateRtcToken("c", 1L, 600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("App Certificate");
    }

    private static byte[] readBytes(ByteBuffer buf) {
        byte[] out = new byte[Short.toUnsignedInt(buf.getShort())];
        buf.get(out);
        return out;
    }

    private static String readString(ByteBuffer buf) {
        return new String(readBytes(buf), StandardCharsets.UTF_8);
    }

    private static byte[] le(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] inflate(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        while (!inflater.finished()) {
            int n = inflater.inflate(chunk);
            if (n == 0 && inflater.needsInput()) {
                break;
            }
            out.write(chunk, 0, n);
        }
        inflater.end();
        return out.toByteArray();
    }
}
