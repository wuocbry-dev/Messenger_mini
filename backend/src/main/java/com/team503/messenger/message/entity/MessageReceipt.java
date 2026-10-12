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

    public MessageReceiptId getId() {
        return id;
    }
}
