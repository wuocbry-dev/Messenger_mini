package com.team503.messenger.message.dto;

import java.time.LocalDateTime;

/**
 * The call a CALL message records. The client words it per viewer
 * ("Missed video call", "Voice call · 3 min", ...).
 */
public record MessageCallResponse(
        Long callId,
        String callType,
        String status,
        Long callerId,
        LocalDateTime startedAt,
        LocalDateTime answeredAt,
        LocalDateTime endedAt,
        /** Talk time from the first answer to the end; null if nobody answered. */
        Long durationSeconds,
        boolean groupCall,
        /** People who actually joined, the caller included. */
        int joinedCount
) {
}
