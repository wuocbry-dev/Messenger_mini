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

    public ConversationMemberId getId() {
        return id;
    }
}
