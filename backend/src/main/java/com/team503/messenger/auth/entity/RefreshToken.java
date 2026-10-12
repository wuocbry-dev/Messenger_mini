package com.team503.messenger.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshToken issue(Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        return new RefreshToken(userId, tokenHash, expiresAt, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }
}
