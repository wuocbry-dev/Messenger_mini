package com.team503.messenger.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team503.messenger.auth.config.JwtProperties;
import com.team503.messenger.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.getAccessTokenTtlSeconds());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("username", user.getUsername());
        payload.put("typ", "access");
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException("Invalid token");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        byte[] expected = sign(unsignedToken).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JwtValidationException("Invalid token signature");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        if (!"access".equals(payload.get("typ"))) {
            throw new JwtValidationException("Invalid token type");
        }

        long expiresAt = numberClaim(payload, "exp");
        if (expiresAt <= clock.instant().getEpochSecond()) {
            throw new JwtValidationException("Token has expired");
        }

        Long userId = Long.valueOf(String.valueOf(payload.get("sub")));
        String username = String.valueOf(payload.get("username"));
        return new AuthenticatedUser(userId, username);
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode JWT", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(value), MAP_TYPE);
        } catch (Exception exception) {
            throw new JwtValidationException("Invalid token payload");
        }
    }

    private long numberClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new JwtValidationException("Invalid token claim");
    }

    private String sign(String unsignedToken) {
        String secret = properties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }
}
