package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;

public record CallResponse(
        Long id,
        Long conversationId,
        Long callerId,
        Long calleeId,
        String callerUsername,
        String calleeUsername,
        String callType,
        String status,
        String agoraChannelName,
        LocalDateTime startedAt,
        LocalDateTime answeredAt,
        LocalDateTime endedAt,
        Long endedBy,
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
