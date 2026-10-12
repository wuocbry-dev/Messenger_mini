package com.team503.messenger.message.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank
        String content
) {
}
