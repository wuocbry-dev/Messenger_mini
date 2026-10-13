package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;
import java.util.List;

public record CallResponse(
        Long id,
        Long conversationId,
        Long callerId,
        /** Only set for 1-to-1 calls. */
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
        String endReason,
        boolean groupCall,
        List<CallParticipantResponse> participants
) {

    public static CallResponse from(
            Call call,
            String callerUsername,
            String calleeUsername,
            List<CallParticipantResponse> participants
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
                call.getEndReason() == null ? null : call.getEndReason().name(),
                participants.size() > 2,
                participants
        );
    }
}
