package com.team503.messenger.call.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "calls")
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long conversationId;

    @Column(name = "caller_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long callerId;

    @Column(name = "callee_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long calleeId;

    @Column(name = "call_type", nullable = false, length = 20)
    private String callType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "agora_channel_name", nullable = false, length = 128)
    private String agoraChannelName;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime startedAt;

    @Column(name = "answered_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime answeredAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime endedAt;

    @Column(name = "ended_by", columnDefinition = "BIGINT UNSIGNED")
    private Long endedBy;

    @Column(name = "end_reason", length = 50)
    private String endReason;

    protected Call() {
    }

    public Long getId() {
        return id;
    }
}
