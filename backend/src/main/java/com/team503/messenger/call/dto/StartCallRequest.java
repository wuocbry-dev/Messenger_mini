package com.team503.messenger.call.dto;

import jakarta.validation.constraints.NotNull;

public record StartCallRequest(
        @NotNull Long conversationId,
        @NotNull Long calleeId
) {
}
