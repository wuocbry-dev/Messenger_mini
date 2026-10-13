package com.team503.messenger.call.service;

import com.team503.messenger.call.config.AgoraProperties;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Agora RTC token generation using the AccessToken2 ("007") format.
 *
 * <p>Layout (integers little-endian, strings are uint16 length + bytes):
 * <pre>
 *   token       = "007" + base64(zlib(pack(signature) + signingInfo))
 *   signingInfo = pack(appId) + uint32 issueTs + uint32 expire + uint32 salt
 *                 + uint16 serviceCount + service...
 *   signingKey  = HMAC(salt, HMAC(issueTs, appCertificate))
 *   service     = uint16 type(1=RTC) + map(uint16 -> uint32) privileges
 *                 + pack(channelName) + pack(uid)
 * </pre>
 *
 * <p>The App Certificate never leaves this class. Tokens are short-lived and
 * never persisted.
 */
@Service
public class DefaultAgoraTokenService implements AgoraTokenService {

    private static final String VERSION = "007";
    private static final int SERVICE_TYPE_RTC = 1;
    private static final int PRIVILEGE_JOIN_CHANNEL = 1;
    private static final int PRIVILEGE_PUBLISH_AUDIO = 2;
    private static final int PRIVILEGE_PUBLISH_VIDEO = 3;

    private final AgoraProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DefaultAgoraTokenService(AgoraProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String generateRtcToken(String channelName, long userId, int expiresInSeconds) {
        String appId = properties.getAppId();
        String certificate = properties.getAppCertificate();
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Agora App ID is not configured");
        }
        if (certificate == null || certificate.isBlank()) {
            throw new IllegalStateException("Agora App Certificate is not configured");
        }

        int ttl = expiresInSeconds > 0
                ? expiresInSeconds
                : Math.toIntExact(properties.getTokenTtlSeconds());
        int issueTs = (int) clock.instant().getEpochSecond();
        int salt = random.nextInt(Integer.MAX_VALUE) + 1;

        // Privilege values are seconds relative to issueTs.
        Map<Integer, Integer> privileges = new TreeMap<>();
        privileges.put(PRIVILEGE_JOIN_CHANNEL, ttl);
        privileges.put(PRIVILEGE_PUBLISH_AUDIO, ttl);
        privileges.put(PRIVILEGE_PUBLISH_VIDEO, ttl);

        ByteArrayOutputStream service = new ByteArrayOutputStream();
        writeUInt16(service, SERVICE_TYPE_RTC);
        writeUInt16(service, privileges.size());
        privileges.forEach((k, v) -> {
            writeUInt16(service, k);
            writeUInt32(service, v);
        });
        writeString(service, channelName);
        writeString(service, Long.toString(userId));

        ByteArrayOutputStream signingInfo = new ByteArrayOutputStream();
        writeString(signingInfo, appId);
        writeUInt32(signingInfo, issueTs);
        writeUInt32(signingInfo, ttl);
        writeUInt32(signingInfo, salt);
        writeUInt16(signingInfo, 1);
        signingInfo.writeBytes(service.toByteArray());

        byte[] signingKey = hmacSha256(uint32(issueTs), certificate.getBytes(StandardCharsets.UTF_8));
        signingKey = hmacSha256(uint32(salt), signingKey);
        byte[] signature = hmacSha256(signingKey, signingInfo.toByteArray());

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        writeBytes(content, signature);
        content.writeBytes(signingInfo.toByteArray());

        return VERSION + Base64.getEncoder().encodeToString(zlib(content.toByteArray()));
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static byte[] zlib(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] uint32(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static void writeUInt16(ByteArrayOutputStream out, int v) {
        out.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array());
    }

    private static void writeUInt32(ByteArrayOutputStream out, int v) {
        out.writeBytes(uint32(v));
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        writeUInt16(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        writeBytes(out, s.getBytes(StandardCharsets.UTF_8));
    }
}
