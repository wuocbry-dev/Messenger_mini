package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Realtime call event published to
 * {@code /user/{username}/queue/calls}. Used to signal incoming calls and
 * notify the other party of state transitions.
 */
@Schema(description = "Realtime call event pushed over STOMP to /user/{username}/queue/calls.")
public record CallEvent(

        @Schema(
                description = "Event kind.",
                example = "INCOMING",
                allowableValues = {"INCOMING", "ACCEPTED", "REJECTED", "CANCELLED", "ENDED"}
        )
        String type,

        @Schema(description = "Call id.", example = "88")
        Long callId,

        @Schema(description = "Owning conversation id.", example = "7")
        Long conversationId,

        @Schema(description = "User id that initiated the call.", example = "1")
        Long callerId,

        @Schema(description = "User id that was called.", example = "42")
        Long calleeId,

        @Schema(description = "Call media type.", example = "VOICE", allowableValues = {"VOICE"})
        String callType,

        @Schema(
                description = "Call status after the event.",
                example = "RINGING",
                allowableValues = {"RINGING", "ACTIVE", "ENDED", "MISSED", "REJECTED", "CANCELED", "FAILED"}
        )
        String status,

        @Schema(description = "Agora channel name to join when accepting.", example = "c-7-1-42-1730000000")
        String agoraChannelName,

        @Schema(description = "When the call was initiated (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime startedAt,

        @Schema(description = "When the call was answered (UTC).", example = "2026-10-13T11:24:08", nullable = true)
        LocalDateTime answeredAt,

        @Schema(description = "When the call ended (UTC).", example = "2026-10-13T11:30:00", nullable = true)
        LocalDateTime endedAt,

        @Schema(description = "Why the call ended.", example = "ENDED", nullable = true)
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
