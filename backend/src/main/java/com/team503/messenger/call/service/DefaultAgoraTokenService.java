package com.team503.messenger.call.service;

import com.team503.messenger.call.config.AgoraProperties;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agora RTC token generation using the official AccessToken2 ("007") format,
 * ported from Agora's reference builder
 * (AgoraIO/Tools DynamicKey/AgoraDynamicKey/java: AccessToken2 + RtcTokenBuilder2).
 *
 * <pre>
 *   signing   = HMAC(key=LE(salt), HMAC(key=LE(issueTs), appCertificate))
 *   content   = str(appId) | LE(issueTs) | LE(expire) | LE(salt) | LE(u16 serviceCount) | serviceRtc
 *   serviceRtc = LE(u16 type=1) | privileges map | str(channelName) | str(uid)
 *   token     = "007" + base64(zlib(str(HMAC(signing, content)) | content))
 * </pre>
 * where {@code str(x)} is a little-endian u16 length followed by the bytes.
 *
 * <p>The App Certificate is held only by this service and is never returned
 * in any DTO or HTTP response, nor logged. Tokens are short-lived and never
 * persisted in MySQL.
 */
@Service
public class DefaultAgoraTokenService implements AgoraTokenService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgoraTokenService.class);

    static final String VERSION = "007";
    private static final short SERVICE_TYPE_RTC = 1;
    static final short PRIVILEGE_JOIN_CHANNEL = 1;
    static final short PRIVILEGE_PUBLISH_AUDIO_STREAM = 2;
    static final short PRIVILEGE_PUBLISH_VIDEO_STREAM = 3;
    static final short PRIVILEGE_PUBLISH_DATA_STREAM = 4;
    /** Same privileges as RtcTokenBuilder2 with Role.ROLE_PUBLISHER. */
    private static final short[] PUBLISHER_PRIVILEGES = {
            PRIVILEGE_JOIN_CHANNEL,
            PRIVILEGE_PUBLISH_AUDIO_STREAM,
            PRIVILEGE_PUBLISH_VIDEO_STREAM,
            PRIVILEGE_PUBLISH_DATA_STREAM
    };
    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgoraProperties properties;

    public DefaultAgoraTokenService(AgoraProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateRtcToken(String channelName, long userId, int expiresInSeconds) {
        String appId = properties.getAppId();
        String appCertificate = properties.getAppCertificate();
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("Agora App ID is not configured");
        }
        if (appCertificate == null || appCertificate.isBlank()) {
            throw new IllegalStateException("Agora App Certificate is not configured");
        }
        if (!HEX_32.matcher(appId).matches()) {
            log.warn("[agora] App ID '{}' is not 32 hex chars; Agora will reject it", appId);
        }
        if (!HEX_32.matcher(appCertificate).matches()) {
            log.warn("[agora] App Certificate is not 32 hex chars (length={}); tokens will be rejected",
                    appCertificate.length());
        }

        int ttl = expiresInSeconds > 0
                ? expiresInSeconds
                : Math.toIntExact(properties.getTokenTtlSeconds());
        int issueTs = (int) (System.currentTimeMillis() / 1000L);
        int salt = RANDOM.nextInt(99_999_999) + 1;

        String token = buildToken(appId, appCertificate, channelName, uidString(userId), ttl, issueTs, salt,
                PUBLISHER_PRIVILEGES);

        log.info("[agora] token issued: appId={} channel={} uid={} ttl={}s issueTs={} expiresAt={} token={}...(len={})",
                appId, channelName, uidString(userId), ttl, issueTs, issueTs + ttl,
                token.substring(0, Math.min(12, token.length())), token.length());
        return token;
    }

    /**
     * Build an AccessToken2 RTC token. Visible for tests so that
     * {@code issueTs} and {@code salt} can be fixed.
     *
     * @param privileges RTC privilege ids in ascending order (the reference
     *                   builder stores them in a TreeMap); each expires after {@code ttl}
     */
    static String buildToken(
            String appId,
            String appCertificate,
            String channelName,
            String uid,
            int ttl,
            int issueTs,
            int salt,
            short[] privileges
    ) {
        Buf content = new Buf()
                .putString(appId)
                .putInt(issueTs)
                .putInt(ttl)
                .putInt(salt)
                .putShort((short) 1); // service count

        content.putShort(SERVICE_TYPE_RTC).putShort((short) privileges.length);
        for (short privilege : privileges) {
            content.putShort(privilege).putInt(ttl);
        }
        content.putString(channelName).putString(uid);

        byte[] signing = hmacSha256(
                new Buf().putInt(salt).toBytes(),
                hmacSha256(new Buf().putInt(issueTs).toBytes(), appCertificate.getBytes(StandardCharsets.UTF_8))
        );
        byte[] contentBytes = content.toBytes();
        byte[] signature = hmacSha256(signing, contentBytes);

        byte[] packed = new Buf().putBytes(signature).putRaw(contentBytes).toBytes();
        return VERSION + Base64.getEncoder().encodeToString(zlib(packed));
    }

    /** Agora encodes numeric uids as unsigned decimal strings; uid 0 means "any". */
    static String uidString(long userId) {
        long uid = userId & 0xFFFFFFFFL;
        return uid == 0 ? "" : Long.toString(uid);
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
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
            byte[] chunk = new byte[1024];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Little-endian packer matching Agora's ByteBuf. */
    private static final class Buf {
        private ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);

        Buf putShort(short v) {
            ensure(Short.BYTES);
            buffer.putShort(v);
            return this;
        }

        Buf putInt(int v) {
            ensure(Integer.BYTES);
            buffer.putInt(v);
            return this;
        }

        Buf putBytes(byte[] v) {
            putShort((short) v.length);
            return putRaw(v);
        }

        Buf putString(String v) {
            return putBytes(v.getBytes(StandardCharsets.UTF_8));
        }

        Buf putRaw(byte[] v) {
            ensure(v.length);
            buffer.put(v);
            return this;
        }

        byte[] toBytes() {
            byte[] out = new byte[buffer.position()];
            System.arraycopy(buffer.array(), 0, out, 0, out.length);
            return out;
        }

        private void ensure(int extra) {
            if (buffer.remaining() >= extra) {
                return;
            }
            ByteBuffer bigger = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, buffer.position() + extra))
                    .order(ByteOrder.LITTLE_ENDIAN);
            buffer.flip();
            bigger.put(buffer);
            buffer = bigger;
        }
    }
}
