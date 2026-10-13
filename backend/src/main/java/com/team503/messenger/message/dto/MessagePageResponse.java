package com.team503.messenger.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of messages, ordered newest first by default.")
public record MessagePageResponse(

        @Schema(description = "Messages on this page.")
        List<MessageResponse> messages,

        @Schema(description = "Zero-based page index.", example = "0")
        int page,

        @Schema(description = "Page size used for the query.", example = "20")
        int size,

        @Schema(description = "Total number of messages in the conversation.", example = "248")
        long totalElements,

        @Schema(description = "Total number of pages.", example = "13")
        int totalPages,

        @Schema(description = "True if more pages exist after this one.", example = "true")
        boolean hasNext
) {
}
