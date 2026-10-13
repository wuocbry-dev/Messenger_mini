package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Delivered/read receipt for one recipient of a message.")
public record MessageReceiptResponse(

        @Schema(description = "Recipient user id.", example = "42")
        Long userId,

        @Schema(description = "Recipient username.", example = "bob")
        String username,

        @Schema(description = "When the recipient's client acknowledged delivery. Null if not yet delivered.", example = "2026-10-13T11:24:05", nullable = true)
        LocalDateTime deliveredAt,

        @Schema(description = "When the recipient opened the message. Null if not yet read.", example = "2026-10-13T11:25:10", nullable = true)
        LocalDateTime readAt
) {
}
