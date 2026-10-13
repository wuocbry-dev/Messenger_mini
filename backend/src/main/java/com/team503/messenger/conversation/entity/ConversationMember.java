package com.team503.messenger.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_members")
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "joined_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime joinedAt;

    @Column(name = "left_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime leftAt;

    protected ConversationMember() {
    }

    private ConversationMember(Long conversationId, Long userId, String role, LocalDateTime joinedAt) {
        this.id = new ConversationMemberId(conversationId, userId);
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static ConversationMember join(Long conversationId, Long userId, LocalDateTime joinedAt) {
        return new ConversationMember(conversationId, userId, "MEMBER", joinedAt);
    }

    public static ConversationMember joinAsOwner(Long conversationId, Long userId, LocalDateTime joinedAt) {
        return new ConversationMember(conversationId, userId, "OWNER", joinedAt);
    }

    /** Bring a member who left back into the conversation. */
    public void rejoin(LocalDateTime now) {
        this.leftAt = null;
        this.joinedAt = now;
    }

    public ConversationMemberId getId() {
        return id;
    }

    public Long getConversationId() {
        return id.getConversationId();
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }
}
