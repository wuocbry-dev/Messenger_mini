package com.team503.messenger.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to open or create a 1-to-1 conversation with another user.")
public record CreateDirectConversationRequest(

        @Schema(
                description = "Id of the other user to start a direct conversation with.",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        Long targetUserId
) {
}
