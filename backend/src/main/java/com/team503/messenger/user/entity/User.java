package com.team503.messenger.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_object_key", length = 512)
    private String avatarObjectKey;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_seen_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime updatedAt;

    protected User() {
    }

    private User(
            String username,
            String email,
            String passwordHash,
            String displayName,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User register(
            String username,
            String email,
            String passwordHash,
            String displayName,
            LocalDateTime now
    ) {
        return new User(username, email, passwordHash, displayName, "ACTIVE", now, now);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void updateLastSeen(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public void updateProfile(String displayName, String avatarObjectKey, LocalDateTime now) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName;
        }
        this.avatarObjectKey = avatarObjectKey;
        this.updatedAt = now;
    }
}
