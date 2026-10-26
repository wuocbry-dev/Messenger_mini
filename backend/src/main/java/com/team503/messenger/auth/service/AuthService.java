package com.team503.messenger.auth.service;

import com.team503.messenger.auth.config.JwtProperties;
import com.team503.messenger.auth.dto.AuthResponse;
import com.team503.messenger.auth.dto.LoginRequest;
import com.team503.messenger.auth.dto.RefreshTokenRequest;
import com.team503.messenger.auth.dto.RegisterRequest;
import com.team503.messenger.auth.entity.RefreshToken;
import com.team503.messenger.auth.exception.AuthException;
import com.team503.messenger.auth.repository.RefreshTokenRepository;
import com.team503.messenger.auth.security.JwtService;
import com.team503.messenger.auth.security.TokenHashing;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already exists");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user = User.register(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                now
        );
        User savedUser = userRepository.save(user);

        return issueAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.usernameOrEmail())
                .or(() -> userRepository.findByEmail(request.usernameOrEmail()))
                .filter(User::isActive)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return issueAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256Hex(request.refreshToken()))
                .orElseThrow(this::invalidRefreshToken);
        LocalDateTime now = LocalDateTime.now(clock);
        if (refreshToken.isRevoked() || refreshToken.isExpired(now)) {
            throw invalidRefreshToken();
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .filter(User::isActive)
                .orElseThrow(this::invalidRefreshToken);

        // Rotate: revoke the presented refresh token and issue a new one.
        refreshToken.revoke(now);

        String newRawRefreshToken = generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                sha256Hex(newRawRefreshToken),
                now.plusDays(jwtProperties.getRefreshTokenTtlDays()),
                now
        ));

        return new AuthResponse(
                jwtService.generateAccessToken(user),
                newRawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256Hex(request.refreshToken()))
                .orElseThrow(this::invalidRefreshToken);
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(LocalDateTime.now(clock));
        }
    }

    private AuthResponse issueAuthResponse(User user) {
        String rawRefreshToken = generateRefreshToken();
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                sha256Hex(rawRefreshToken),
                now.plusDays(jwtProperties.getRefreshTokenTtlDays()),
                now
        ));

        return new AuthResponse(
                jwtService.generateAccessToken(user),
                rawRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                UserResponse.from(user)
        );
    }

    private String generateRefreshToken() {
        return TokenHashing.generateToken();
    }

    private String sha256Hex(String value) {
        return TokenHashing.sha256Hex(value);
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username/email or password");
    }

    private AuthException invalidRefreshToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid");
    }
}
