package com.team503.messenger.conversation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupConversationRequest(
        /** Optional; the UI falls back to the member names when blank. */
        @Size(max = 150)
        String title,

        /** Other members (the creator is added automatically). */
        @NotEmpty
        @Size(max = 49)
        List<Long> memberIds
) {
}
