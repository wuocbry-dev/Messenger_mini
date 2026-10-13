package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body of POST /conversations/{id}/messages.")
public record SendMessageRequest(

        @Schema(
                description = "Plain text message body. HTML is not rendered.",
                example = "Hello, are you free for a call?",
                maxLength = 4000,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 4000)
        String content
) {
}
