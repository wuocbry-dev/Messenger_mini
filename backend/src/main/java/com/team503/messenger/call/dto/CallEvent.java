package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;

/**
 * Realtime call event published to
 * {@code /user/{username}/queue/calls}. Used to signal incoming calls and
 * notify the other party of state transitions.
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
        ENDED
    }

    public static CallEvent incoming(Call call) {
        return build(EventType.INCOMING, call);
    }

    public static CallEvent accepted(Call call) {
        return build(EventType.ACCEPTED, call);
    }

    public static CallEvent rejected(Call call) {
        return build(EventType.REJECTED, call);
    }

    public static CallEvent cancelled(Call call) {
        return build(EventType.CANCELLED, call);
    }

    public static CallEvent ended(Call call) {
        return build(EventType.ENDED, call);
    }

    private static CallEvent build(EventType type, Call call) {
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
                call.getEndReason()
        );
    }
}
