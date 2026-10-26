package com.team503.messenger.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.dto.ForgotPasswordRequest;
import com.team503.messenger.auth.dto.ResetPasswordRequest;
import com.team503.messenger.auth.entity.PasswordResetToken;
import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.auth.repository.PasswordResetTokenRepository;
import com.team503.messenger.auth.repository.RefreshTokenRepository;
import com.team503.messenger.auth.security.TokenHashing;
import com.team503.messenger.notification.config.MailProperties;
import com.team503.messenger.notification.dto.EmailContent;
import com.team503.messenger.notification.service.EmailService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final long USER_ID = 7L;
    private static final String EMAIL = "khoi@example.com";

    private UserRepository userRepository;
    private PasswordResetTokenRepository resetTokenRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resetTokenRepository = mock(PasswordResetTokenRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);

        MailProperties mailProperties = new MailProperties();
        mailProperties.setAppUrl("https://chat.example.com");

        service = new PasswordResetService(userRepository, resetTokenRepository, refreshTokenRepository,
                passwordEncoder, emailService, mailProperties, CLOCK);

        user = User.register("khoi", EMAIL, "old-hash", "Khôi", NOW.minusDays(10));
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void requestResetStoresHashedTokenAndEmailsMatchingLink() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(saved.capture());
        verify(resetTokenRepository).markAllUnusedAsUsed(USER_ID, NOW);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(PasswordResetService.TOKEN_TTL));

        ArgumentCaptor<EmailContent> email = ArgumentCaptor.forClass(EmailContent.class);
        verify(emailService).send(eq(EMAIL), anyString(), email.capture());
        String link = email.getValue().actionUrl();
        assertThat(link).startsWith("https://chat.example.com/reset-password?token=");

        // The emailed token is the pre-image of the stored hash, never the hash itself.
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        assertThat(TokenHashing.sha256Hex(rawToken)).isEqualTo(saved.getValue().getTokenHash());
    }

    @Test
    void requestResetForUnknownEmailDoesNothingAndDoesNotFail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        verifyNoInteractions(resetTokenRepository, emailService);
    }

    @Test
    void requestResetIsThrottledWithinCooldown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(resetTokenRepository.existsByUserIdAndCreatedAtAfter(USER_ID, NOW.minus(PasswordResetService.RESEND_COOLDOWN)))
                .thenReturn(true);

        service.requestReset(new ForgotPasswordRequest(EMAIL));

        verify(resetTokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resetPasswordChangesHashConsumesTokenAndRevokesSessions() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TokenHashing.sha256Hex("raw"), NOW.plusMinutes(5), NOW);
        when(resetTokenRepository.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword(new ResetPasswordRequest("raw", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(USER_ID, NOW);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TokenHashing.sha256Hex("raw"), NOW, NOW.minusMinutes(30));
        when(resetTokenRepository.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));

        assertInvalidToken(() -> service.resetPassword(new ResetPasswordRequest("raw", "new-password")));
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TokenHashing.sha256Hex("raw"), NOW.plusMinutes(5), NOW);
        token.markUsed(NOW);
        when(resetTokenRepository.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));

        assertInvalidToken(() -> service.resetPassword(new ResetPasswordRequest("raw", "new-password")));
    }

    @Test
    void resetPasswordRejectsUnknownToken() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertInvalidToken(() -> service.resetPassword(new ResetPasswordRequest("nope", "new-password")));
        verifyNoInteractions(refreshTokenRepository);
    }

    private static void assertInvalidToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> {
                    AuthException auth = (AuthException) ex;
                    assertThat(auth.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(auth.getCode()).isEqualTo("INVALID_RESET_TOKEN");
                });
    }
}
