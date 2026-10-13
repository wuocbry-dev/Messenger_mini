package com.team503.messenger.call.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Start a new 1-to-1 voice call. Caller id is taken from the JWT principal.")
public record StartCallRequest(

        @Schema(description = "Conversation in which the call is started.", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long conversationId,

        @Schema(description = "User id being called.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long calleeId
) {
}
