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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long conversationId;

    @Column(name = "sender_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long senderId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    protected Message() {
    }

    public Long getId() {
        return id;
    }
}
