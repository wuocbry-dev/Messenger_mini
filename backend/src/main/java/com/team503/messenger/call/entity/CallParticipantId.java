package com.team503.messenger.call.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CallParticipantId implements Serializable {

    @Column(name = "call_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long callId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    protected CallParticipantId() {
    }

    public CallParticipantId(Long callId, Long userId) {
        this.callId = callId;
        this.userId = userId;
    }

    public Long getCallId() {
        return callId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallParticipantId other)) {
            return false;
        }
        return Objects.equals(callId, other.callId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId, userId);
    }
}
