package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Realtime call event published to the {@code /user/queue/calls} user
 * destination. Every event carries the full participant list, so a client can
 * rebuild the call state from any single event.
 */
public record CallEvent(
        String type,
        Long callId,
        Long conversationId,
        Long callerId,
        /** The user whose action caused the event (null for system events). */
        Long actorId,
        String callType,
        String status,
        String agoraChannelName,
        LocalDateTime startedAt,
        LocalDateTime answeredAt,
        LocalDateTime endedAt,
        String endReason,
        List<CallParticipantResponse> participants
) {

    public enum EventType {
        /** Sent only to people being rung. */
        INCOMING,
        /** The first invitee answered. */
        ACCEPTED,
        REJECTED,
        CANCELLED,
        MISSED,
        ENDED,
        PARTICIPANT_INVITED,
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        PARTICIPANT_DECLINED,
        /** Voice call switched to video. */
        MEDIA_UPGRADED,
        /** The call moved to a new group conversation. */
        CONVERSATION_CHANGED
    }

    public static CallEvent of(
            EventType type,
            Call call,
            Long actorId,
            List<CallParticipantResponse> participants
    ) {
        return new CallEvent(
                type.name(),
                call.getId(),
                call.getConversationId(),
                call.getCallerId(),
                actorId,
                call.getCallType().name(),
                call.getStatus().name(),
                call.getAgoraChannelName(),
                call.getStartedAt(),
                call.getAnsweredAt(),
                call.getEndedAt(),
                call.getEndReason() == null ? null : call.getEndReason().name(),
                participants
        );
    }
}
