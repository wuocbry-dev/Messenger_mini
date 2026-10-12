package com.team503.messenger.message.dto;

import java.time.LocalDateTime;

public record MessageReceiptResponse(
        Long userId,
        String username,
        LocalDateTime deliveredAt,
        LocalDateTime readAt
) {
}
