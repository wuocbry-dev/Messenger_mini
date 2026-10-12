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

    public enum CallType {
        VOICE,
        VIDEO
    }

    public enum Status {
        RINGING,
        ACCEPTED,
        REJECTED,
        MISSED,
        CANCELLED,
        ENDED,
        FAILED;

        public boolean isTerminal() {
            return this == REJECTED
                    || this == MISSED
                    || this == CANCELLED
                    || this == ENDED
                    || this == FAILED;
        }
    }

    public enum EndReason {
        CANCELLED_BY_CALLER,
        REJECTED_BY_CALLEE,
        ENDED_BY_CALLER,
        ENDED_BY_CALLEE,
        MISSED_TIMEOUT,
        FAILED_NETWORK
    }

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

    private Call(
            Long conversationId,
            Long callerId,
            Long calleeId,
            String callType,
            String agoraChannelName,
            LocalDateTime startedAt
    ) {
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
        this.status = Status.RINGING.name();
        this.agoraChannelName = agoraChannelName;
        this.startedAt = startedAt;
    }

    public static Call ringing(
            Long conversationId,
            Long callerId,
            Long calleeId,
            CallType type,
            String agoraChannelName,
            LocalDateTime now
    ) {
        if (callerId.equals(calleeId)) {
            throw new IllegalArgumentException("Caller and callee must be different users");
        }
        return new Call(
                conversationId,
                callerId,
                calleeId,
                type.name(),
                agoraChannelName,
                now
        );
    }

    public void markAccepted(LocalDateTime now) {
        requireStatus(Status.RINGING);
        this.status = Status.ACCEPTED.name();
        this.answeredAt = now;
    }

    public void markRejected(LocalDateTime now) {
        requireStatus(Status.RINGING);
        this.status = Status.REJECTED.name();
        this.endedAt = now;
        this.endedBy = calleeId;
        this.endReason = EndReason.REJECTED_BY_CALLEE.name();
    }

    public void markCancelled(LocalDateTime now) {
        requireStatus(Status.RINGING);
        this.status = Status.CANCELLED.name();
        this.endedAt = now;
        this.endedBy = callerId;
        this.endReason = EndReason.CANCELLED_BY_CALLER.name();
    }

    public void markEnded(Long actorId, EndReason reason, LocalDateTime now) {
        requireStatus(Status.ACCEPTED);
        this.status = Status.ENDED.name();
        this.endedAt = now;
        this.endedBy = actorId;
        this.endReason = reason.name();
    }

    public void markMissed(LocalDateTime now) {
        requireStatus(Status.RINGING);
        this.status = Status.MISSED.name();
        this.endedAt = now;
        this.endReason = EndReason.MISSED_TIMEOUT.name();
    }

    public void markFailed(LocalDateTime now, String reason) {
        // Failed can be reached from any non-terminal state.
        if (Status.valueOf(status).isTerminal()) {
            return;
        }
        this.status = Status.FAILED.name();
        this.endedAt = now;
        this.endReason = reason != null ? reason : EndReason.FAILED_NETWORK.name();
    }

    private void requireStatus(Status expected) {
        if (Status.valueOf(status) != expected) {
            throw new IllegalStateException(
                    "Call is in status " + status + " but expected " + expected
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getCallerId() {
        return callerId;
    }

    public Long getCalleeId() {
        return calleeId;
    }

    public CallType getCallType() {
        return CallType.valueOf(callType);
    }

    public Status getStatus() {
        return Status.valueOf(status);
    }

    public String getRawStatus() {
        return status;
    }

    public String getAgoraChannelName() {
        return agoraChannelName;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public Long getEndedBy() {
        return endedBy;
    }

    public String getEndReason() {
        return endReason;
    }
}
