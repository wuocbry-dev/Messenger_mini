package com.team503.messenger.call.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * A 1-to-1 call. Holds business state and history only; it never stores
 * Agora tokens or the App Certificate.
 */
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
            return this != RINGING && this != ACCEPTED;
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

    /** The single invitee of a 1-to-1 call; null for calls started in a group. */
    @Column(name = "callee_id", columnDefinition = "BIGINT UNSIGNED")
    private Long calleeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 20)
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 50)
    private EndReason endReason;

    protected Call() {
    }

    private Call(Long conversationId, Long callerId, Long calleeId, CallType callType,
                 String agoraChannelName, LocalDateTime startedAt) {
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
        this.status = Status.RINGING;
        this.agoraChannelName = agoraChannelName;
        this.startedAt = startedAt;
    }

    public static Call ringing(Long conversationId, Long callerId, Long calleeId, CallType type,
                               String agoraChannelName, LocalDateTime now) {
        if (callerId.equals(calleeId)) {
            throw new IllegalArgumentException("Caller and callee must be different users");
        }
        // calleeId may be null for a call started in a group conversation.
        return new Call(conversationId, callerId, calleeId, type, agoraChannelName, now);
    }

    public void markAccepted(LocalDateTime now) {
        require(Status.RINGING);
        this.status = Status.ACCEPTED;
        this.answeredAt = now;
    }

    public void markRejected(Long actorId, LocalDateTime now) {
        require(Status.RINGING);
        finish(Status.REJECTED, actorId, EndReason.REJECTED_BY_CALLEE, now);
    }

    public void markCancelled(LocalDateTime now) {
        require(Status.RINGING);
        finish(Status.CANCELLED, callerId, EndReason.CANCELLED_BY_CALLER, now);
    }

    public void markMissed(LocalDateTime now) {
        require(Status.RINGING);
        finish(Status.MISSED, null, EndReason.MISSED_TIMEOUT, now);
    }

    public void markEnded(Long actorId, LocalDateTime now) {
        require(Status.ACCEPTED);
        EndReason reason = callerId.equals(actorId) ? EndReason.ENDED_BY_CALLER : EndReason.ENDED_BY_CALLEE;
        finish(Status.ENDED, actorId, reason, now);
    }

    public void markFailed(LocalDateTime now) {
        if (status.isTerminal()) {
            return;
        }
        finish(Status.FAILED, null, EndReason.FAILED_NETWORK, now);
    }

    /** The call moved to another conversation (e.g. a new group chat was created). */
    public void changeConversation(Long newConversationId) {
        this.conversationId = newConversationId;
    }

    /** Voice call switched to video; a no-op if it already is. */
    public void upgradeToVideo() {
        this.callType = CallType.VIDEO;
    }

    private void finish(Status newStatus, Long actor, EndReason reason, LocalDateTime now) {
        this.status = newStatus;
        this.endedAt = now;
        this.endedBy = actor;
        this.endReason = reason;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Call is " + status + " but expected " + expected);
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
        return callType;
    }

    public Status getStatus() {
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

    public EndReason getEndReason() {
        return endReason;
    }
}
