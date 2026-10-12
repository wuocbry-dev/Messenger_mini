package com.team503.messenger.conversation.dto;

import jakarta.validation.constraints.NotNull;

public record CreateDirectConversationRequest(
        @NotNull
        Long targetUserId
) {
}
