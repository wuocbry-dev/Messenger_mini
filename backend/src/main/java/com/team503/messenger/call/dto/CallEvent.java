package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;

/**
 * Realtime call event published to the {@code /user/queue/calls} user
 * destination. Signals incoming calls and state transitions to the two
 * participants only.
 */
public record CallEvent(
        String type,
        Long callId,
        Long conversationId,
        Long callerId,
        Long calleeId,
        String callType,
        String status,
        String agoraChannelName,
        LocalDateTime startedAt,
        LocalDateTime answeredAt,
        LocalDateTime endedAt,
        String endReason
) {

    public enum EventType {
        INCOMING,
        ACCEPTED,
        REJECTED,
        CANCELLED,
        MISSED,
        ENDED
    }

    public static CallEvent of(EventType type, Call call) {
        return new CallEvent(
                type.name(),
                call.getId(),
                call.getConversationId(),
                call.getCallerId(),
                call.getCalleeId(),
                call.getCallType().name(),
                call.getStatus().name(),
                call.getAgoraChannelName(),
                call.getStartedAt(),
                call.getAnsweredAt(),
                call.getEndedAt(),
                call.getEndReason() == null ? null : call.getEndReason().name()
        );
    }
}
