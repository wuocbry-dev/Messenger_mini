package com.team503.messenger.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    public enum Type {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM,
        /** A finished voice/video call; details come from the linked call. */
        CALL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long conversationId;

    @Column(name = "sender_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long senderId;

    @Column(name = "call_id", columnDefinition = "BIGINT UNSIGNED")
    private Long callId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    protected Message() {
    }

    private Message(Long conversationId, Long senderId, String type, String content, LocalDateTime createdAt) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Message text(Long conversationId, Long senderId, String content, LocalDateTime createdAt) {
        return new Message(conversationId, senderId, Type.TEXT.name(), content.trim(), createdAt);
    }

    public static Message of(Long conversationId, Long senderId, Type type, String content, LocalDateTime createdAt) {
        return new Message(conversationId, senderId, type.name(), content == null ? null : content.trim(), createdAt);
    }

    /** The timeline entry for a finished call, shown on the caller's side. */
    public static Message call(Long conversationId, Long callerId, Long callId, LocalDateTime createdAt) {
        Message message = new Message(conversationId, callerId, Type.CALL.name(), null, createdAt);
        message.callId = callId;
        return message;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getCallId() {
        return callId;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isText() {
        return Type.TEXT.name().equals(type);
    }
}
