package com.team503.messenger.call.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One person's involvement in a call (the caller, an invitee, or a later joiner). */
@Entity
@Table(name = "call_participants")
public class CallParticipant {

    public enum State {
        /** Ringing for this person. */
        INVITED,
        /** In the call (the caller starts here). */
        JOINED,
        DECLINED,
        LEFT,
        /** Never answered (timeout or the caller hung up first). */
        MISSED
    }

    @EmbeddedId
    private CallParticipantId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private State state;

    @Column(name = "invited_by", columnDefinition = "BIGINT UNSIGNED")
    private Long invitedBy;

    @Column(name = "invited_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime invitedAt;

    @Column(name = "joined_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime joinedAt;

    @Column(name = "left_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime leftAt;

    protected CallParticipant() {
    }

    private CallParticipant(Long callId, Long userId, State state, Long invitedBy, LocalDateTime now) {
        this.id = new CallParticipantId(callId, userId);
        this.state = state;
        this.invitedBy = invitedBy;
        this.invitedAt = now;
        if (state == State.JOINED) {
            this.joinedAt = now;
        }
    }

    public static CallParticipant caller(Long callId, Long userId, LocalDateTime now) {
        return new CallParticipant(callId, userId, State.JOINED, null, now);
    }

    public static CallParticipant invited(Long callId, Long userId, Long invitedBy, LocalDateTime now) {
        return new CallParticipant(callId, userId, State.INVITED, invitedBy, now);
    }

    /** Someone who joins a running call on their own, without being rung. */
    public static CallParticipant joiner(Long callId, Long userId, LocalDateTime now) {
        return new CallParticipant(callId, userId, State.JOINED, null, now);
    }

    /** Joins a running call again after having declined, missed or left it. */
    public void rejoin(LocalDateTime now) {
        if (state == State.INVITED || state == State.JOINED) {
            throw new IllegalStateException("Participant is already in the call");
        }
        this.state = State.JOINED;
        if (this.joinedAt == null) {
            this.joinedAt = now;
        }
        this.leftAt = null;
    }

    public void join(LocalDateTime now) {
        require(State.INVITED);
        this.state = State.JOINED;
        this.joinedAt = now;
    }

    public void decline(LocalDateTime now) {
        require(State.INVITED);
        this.state = State.DECLINED;
        this.leftAt = now;
    }

    public void leave(LocalDateTime now) {
        require(State.JOINED);
        this.state = State.LEFT;
        this.leftAt = now;
    }

    /** Ringing that nobody answered. */
    public void miss(LocalDateTime now) {
        require(State.INVITED);
        this.state = State.MISSED;
        this.leftAt = now;
    }

    /** Ring someone again after they declined, missed or left. */
    public void reinvite(Long invitedBy, LocalDateTime now) {
        if (state == State.INVITED || state == State.JOINED) {
            throw new IllegalStateException("Participant is already in the call");
        }
        this.state = State.INVITED;
        this.invitedBy = invitedBy;
        this.invitedAt = now;
        this.joinedAt = null;
        this.leftAt = null;
    }

    public boolean isIn(State... states) {
        for (State s : states) {
            if (state == s) {
                return true;
            }
        }
        return false;
    }

    private void require(State expected) {
        if (state != expected) {
            throw new IllegalStateException("Participant is " + state + " but expected " + expected);
        }
    }

    public Long getCallId() {
        return id.getCallId();
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public State getState() {
        return state;
    }

    public Long getInvitedBy() {
        return invitedBy;
    }

    public LocalDateTime getInvitedAt() {
        return invitedAt;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }
}
