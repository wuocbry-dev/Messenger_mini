package com.team503.messenger.call.dto;

import java.util.List;

public record CallHistoryResponse(
        List<CallResponse> calls,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
