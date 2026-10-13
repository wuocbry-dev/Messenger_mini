package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.Conversation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Conversation row used in lists (sidebar).")
public record ConversationResponse(

        @Schema(description = "Conversation id.", example = "7")
        Long id,

        @Schema(
                description = "Conversation type.",
                example = "DIRECT",
                allowableValues = {"DIRECT", "GROUP"}
        )
        String type,

        @Schema(
                description = "Internal deduplication key for direct conversations. Always null for groups.",
                example = "1:42",
                nullable = true
        )
        String directKey,

        @Schema(
                description = "Conversation title. For direct chats this is the other user's display name.",
                example = "Alice"
        )
        String title,

        @Schema(
                description = "R2 object key for the conversation avatar, or null.",
                example = "messenger/avatars/42.png",
                nullable = true
        )
        String avatarObjectKey,

        @Schema(description = "Conversation creation timestamp (UTC).", example = "2026-10-01T08:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Last activity timestamp (UTC).", example = "2026-10-13T11:24:00")
        LocalDateTime updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getDirectKey(),
                conversation.getTitle(),
                conversation.getAvatarObjectKey(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
