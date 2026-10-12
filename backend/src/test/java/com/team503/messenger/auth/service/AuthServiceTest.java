package com.team503.messenger.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team503.messenger.auth.config.JwtProperties;
import com.team503.messenger.auth.dto.LoginRequest;
import com.team503.messenger.auth.dto.RefreshTokenRequest;
import com.team503.messenger.auth.dto.RegisterRequest;
import com.team503.messenger.auth.entity.RefreshToken;
import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.auth.repository.RefreshTokenRepository;
import com.team503.messenger.auth.security.JwtService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String JWT_SECRET = "12345678901234567890123456789012";

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(JWT_SECRET);
        jwtProperties.setAccessTokenTtlSeconds(900);
        jwtProperties.setRefreshTokenTtlDays(30);

        JwtService jwtService = new JwtService(jwtProperties, new ObjectMapper(), CLOCK);
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                jwtProperties,
                CLOCK
        );
    }

    @Test
    void registerCreatesActiveUserAndStoresHashedCredentials() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        var response = authService.register(new RegisterRequest(
                "quocbry",
                "quoc@example.com",
                "password123",
                "Quoc Bry"
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("quocbry");

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(response.refreshToken());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("quocbry")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "quocbry",
                "quoc@example.com",
                "password123",
                "Quoc Bry"
        )))
                .isInstanceOf(AuthException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("quoc@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "quocbry",
                "quoc@example.com",
                "password123",
                "Quoc Bry"
        )))
                .isInstanceOf(AuthException.class)
                .hasMessage("Email already exists");
    }

    @Test
    void loginReturnsTokensForActiveUser() {
        User user = activeUser(1L, "quocbry", "quoc@example.com", "password123");
        when(userRepository.findByUsername("quocbry")).thenReturn(Optional.of(user));

        var response = authService.login(new LoginRequest("quocbry", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(1L);
    }

    @Test
    void loginRejectsBadPassword() {
        User user = activeUser(1L, "quocbry", "quoc@example.com", "password123");
        when(userRepository.findByUsername("quocbry")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("quocbry", "wrong-password")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid username/email or password");
    }

    @Test
    void refreshReturnsNewAccessTokenForValidRefreshToken() {
        User user = activeUser(1L, "quocbry", "quoc@example.com", "password123");
        String rawRefreshToken = "valid-refresh-token";
        RefreshToken refreshToken = RefreshToken.issue(
                1L,
                sha256Hex(rawRefreshToken),
                LocalDateTime.now(CLOCK).plusDays(1),
                LocalDateTime.now(CLOCK)
        );
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = authService.refresh(new RefreshTokenRequest(rawRefreshToken));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo(rawRefreshToken);
        assertThat(response.user().username()).isEqualTo("quocbry");
    }

    @Test
    void refreshRejectsRevokedRefreshToken() {
        String rawRefreshToken = "revoked-refresh-token";
        RefreshToken refreshToken = RefreshToken.issue(
                1L,
                sha256Hex(rawRefreshToken),
                LocalDateTime.now(CLOCK).plusDays(1),
                LocalDateTime.now(CLOCK)
        );
        refreshToken.revoke(LocalDateTime.now(CLOCK));
        when(refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))).thenReturn(Optional.of(refreshToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rawRefreshToken)))
                .isInstanceOf(AuthException.class)
                .hasMessage("Refresh token is invalid");
    }

    private User activeUser(Long id, String username, String email, String password) {
        User user = User.register(
                username,
                email,
                passwordEncoder.encode(password),
                "Quoc Bry",
                LocalDateTime.now(CLOCK)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
