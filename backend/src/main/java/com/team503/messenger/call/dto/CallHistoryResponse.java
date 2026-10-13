package com.team503.messenger.call.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated call history. Most recent call first.")
public record CallHistoryResponse(

        @Schema(description = "Calls on this page, newest first.")
        List<CallResponse> calls,

        @Schema(description = "Zero-based page index.", example = "0")
        int page,

        @Schema(description = "Page size used for the query.", example = "20")
        int size,

        @Schema(description = "Total number of calls in the history scope.", example = "42")
        long totalElements,

        @Schema(description = "Total number of pages.", example = "3")
        int totalPages,

        @Schema(description = "True if more pages exist after this one.", example = "false")
        boolean hasNext
) {
}
