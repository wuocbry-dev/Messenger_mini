package com.team503.messenger.auth.service;

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
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    /** Minimum gap between two reset emails for the same account. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MailProperties mailProperties;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository resetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            MailProperties mailProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    /**
     * Emails a reset link when the address belongs to an active account. Always
     * completes silently so the endpoint cannot be used to discover registered emails.
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email().trim())
                .filter(User::isActive)
                .orElse(null);
        if (user == null) {
            log.debug("Password reset requested for unknown or inactive email");
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (resetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(RESEND_COOLDOWN))) {
            log.debug("Password reset throttled for user {}", user.getId());
            return;
        }

        // Only the newest link works.
        resetTokenRepository.markAllUnusedAsUsed(user.getId(), now);

        String rawToken = TokenHashing.generateToken();
        resetTokenRepository.save(PasswordResetToken.issue(
                user.getId(),
                TokenHashing.sha256Hex(rawToken),
                now.plus(TOKEN_TTL),
                now
        ));

        emailService.send(user.getEmail(), "Đặt lại mật khẩu Messenger Mini", resetEmail(user, rawToken));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenHashing.sha256Hex(request.token()))
                .filter(t -> !t.isUsed() && !t.isExpired(now))
                .orElseThrow(PasswordResetService::invalidToken);

        User user = userRepository.findById(token.getUserId())
                .filter(User::isActive)
                .orElseThrow(PasswordResetService::invalidToken);

        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        token.markUsed(now);
        resetTokenRepository.markAllUnusedAsUsed(user.getId(), now);
        // Sign out every device that might be in the wrong hands.
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
    }

    private EmailContent resetEmail(User user, String rawToken) {
        String link = UriComponentsBuilder.fromHttpUrl(mailProperties.getAppUrl())
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        return EmailContent.of(
                        "Đặt lại mật khẩu",
                        "Xin chào " + user.getDisplayName() + ",",
                        "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản @" + user.getUsername()
                                + ". Nhấn nút bên dưới để tạo mật khẩu mới. Liên kết có hiệu lực trong "
                                + TOKEN_TTL.toMinutes() + " phút và chỉ dùng được một lần.")
                .withPreheader("Liên kết đặt lại mật khẩu của bạn (hết hạn sau " + TOKEN_TTL.toMinutes() + " phút)")
                .withAction("Đặt lại mật khẩu", link)
                .withFootnote("Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. "
                        + "Mật khẩu hiện tại của bạn vẫn giữ nguyên.");
    }

    private static AuthException invalidToken() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                "This reset link is invalid or has expired. Please request a new one.");
    }
}
