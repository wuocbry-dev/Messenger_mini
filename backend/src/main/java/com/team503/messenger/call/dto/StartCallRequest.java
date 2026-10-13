package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.Call;
import jakarta.validation.constraints.NotNull;

public record StartCallRequest(
        @NotNull Long conversationId,
        /**
         * The person to ring in a 1-to-1 conversation. Omit it to ring every
         * other member of the conversation (group calls).
         */
        Long calleeId,
        /** VOICE or VIDEO; defaults to VOICE when omitted. */
        Call.CallType callType
) {

    public Call.CallType callTypeOrDefault() {
        return callType != null ? callType : Call.CallType.VOICE;
    }
}
