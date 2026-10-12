package com.team503.messenger.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "direct_key", length = 64)
    private String directKey;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "avatar_object_key", length = 512)
    private String avatarObjectKey;

    @Column(name = "created_by", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime updatedAt;

    protected Conversation() {
    }

    private Conversation(
            String type,
            String directKey,
            String title,
            String avatarObjectKey,
            Long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.type = type;
        this.directKey = directKey;
        this.title = title;
        this.avatarObjectKey = avatarObjectKey;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Conversation createDirect(
            Long userId1,
            Long userId2,
            Long createdBy,
            String directKey,
            LocalDateTime now
    ) {
        return new Conversation(
                "DIRECT",
                directKey,
                null,
                null,
                createdBy,
                now,
                now
        );
    }

    public static String generateDirectKey(Long userId1, Long userId2) {
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);
        String pair = minId + ":" + maxId;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pair.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDirectKey() {
        return directKey;
    }

    public String getTitle() {
        return title;
    }

    public String getAvatarObjectKey() {
        return avatarObjectKey;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }
}
