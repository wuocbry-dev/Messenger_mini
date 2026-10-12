package com.team503.messenger.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_receipts")
public class MessageReceipt {

    @EmbeddedId
    private MessageReceiptId id;

    @Column(name = "delivered_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime readAt;

    protected MessageReceipt() {
    }

    private MessageReceipt(Long messageId, Long userId) {
        this.id = new MessageReceiptId(messageId, userId);
    }

    public static MessageReceipt forMessage(Long messageId, Long recipientId, LocalDateTime deliveredAt) {
        MessageReceipt receipt = new MessageReceipt(messageId, recipientId);
        receipt.deliveredAt = deliveredAt;
        return receipt;
    }

    public MessageReceiptId getId() {
        return id;
    }

    public Long getMessageId() {
        return id.getMessageId();
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void markDelivered(LocalDateTime now) {
        if (this.deliveredAt == null) {
            this.deliveredAt = now;
        }
    }

    public void markRead(LocalDateTime now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
