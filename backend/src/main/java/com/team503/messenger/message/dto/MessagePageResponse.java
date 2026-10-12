package com.team503.messenger.message.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> messages,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
