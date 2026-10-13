package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Full conversation detail with members.")
public record ConversationDetailResponse(

        @Schema(description = "Conversation id.", example = "7")
        Long id,

        @Schema(
                description = "Conversation type.",
                example = "DIRECT",
                allowableValues = {"DIRECT", "GROUP"}
        )
        String type,

        @Schema(description = "Direct conversation dedup key. Null for groups.", example = "1:42", nullable = true)
        String directKey,

        @Schema(description = "Conversation title.", example = "Alice")
        String title,

        @Schema(description = "R2 object key for the conversation avatar, or null.", nullable = true)
        String avatarObjectKey,

        @Schema(description = "Conversation creation timestamp (UTC).", example = "2026-10-01T08:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Last activity timestamp (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime updatedAt,

        @Schema(description = "Conversation members, including the requesting user.")
        List<ConversationMemberResponse> members
) {

    public static ConversationDetailResponse from(Conversation conversation, List<ConversationMemberResponse> members) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getDirectKey(),
                conversation.getTitle(),
                conversation.getAvatarObjectKey(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                members
        );
    }
}
