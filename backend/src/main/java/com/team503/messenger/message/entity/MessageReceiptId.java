package com.team503.messenger.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MessageReceiptId implements Serializable {

    @Column(name = "message_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long messageId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    protected MessageReceiptId() {
    }

    public MessageReceiptId(Long messageId, Long userId) {
        this.messageId = messageId;
        this.userId = userId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageReceiptId that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, userId);
    }
}
