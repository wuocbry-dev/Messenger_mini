package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Call metadata. Channel name and Agora token are issued separately.")
public record CallResponse(

        @Schema(description = "Call id.", example = "88")
        Long id,

        @Schema(description = "Owning conversation id.", example = "7")
        Long conversationId,

        @Schema(description = "User id that initiated the call.", example = "1")
        Long callerId,

        @Schema(description = "User id that was called.", example = "42")
        Long calleeId,

        @Schema(description = "Username of the caller.", example = "alice")
        String callerUsername,

        @Schema(description = "Username of the callee.", example = "bob")
        String calleeUsername,

        @Schema(
                description = "Call media type.",
                example = "VOICE",
                allowableValues = {"VOICE"}
        )
        String callType,

        @Schema(
                description = "Current call status.",
                example = "RINGING",
                allowableValues = {"RINGING", "ACTIVE", "ENDED", "MISSED", "REJECTED", "CANCELED", "FAILED"}
        )
        String status,

        @Schema(description = "Server-allocated Agora channel name.", example = "c-7-1-42-1730000000")
        String agoraChannelName,

        @Schema(description = "When the call was initiated (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime startedAt,

        @Schema(description = "When the callee accepted (UTC). Null if not answered.", example = "2026-10-13T11:24:08", nullable = true)
        LocalDateTime answeredAt,

        @Schema(description = "When the call ended (UTC). Null while still active.", example = "2026-10-13T11:30:00", nullable = true)
        LocalDateTime endedAt,

        @Schema(description = "User id that ended the call. Null while still active.", example = "1", nullable = true)
        Long endedBy,

        @Schema(
                description = "Why the call ended.",
                example = "ENDED",
                allowableValues = {"ENDED", "REJECTED", "CANCELED", "MISSED", "FAILED"},
                nullable = true
        )
        String endReason
) {

    public static CallResponse from(
            Call call,
            String callerUsername,
            String calleeUsername
    ) {
        return new CallResponse(
                call.getId(),
                call.getConversationId(),
                call.getCallerId(),
                call.getCalleeId(),
                callerUsername,
                calleeUsername,
                call.getCallType().name(),
                call.getStatus().name(),
                call.getAgoraChannelName(),
                call.getStartedAt(),
                call.getAnsweredAt(),
                call.getEndedAt(),
                call.getEndedBy(),
                call.getEndReason()
        );
    }
}
