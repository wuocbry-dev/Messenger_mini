package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.ConversationMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "One member of a conversation.")
public record ConversationMemberResponse(

        @Schema(description = "User id.", example = "1")
        Long userId,

        @Schema(description = "Username.", example = "alice")
        String username,

        @Schema(description = "Display name.", example = "Alice")
        String displayName,

        @Schema(description = "R2 avatar object key, or null.", example = "messenger/avatars/1.png", nullable = true)
        String avatarObjectKey,

        @Schema(
                description = "Role in the conversation.",
                example = "MEMBER",
                allowableValues = {"MEMBER", "ADMIN", "OWNER"}
        )
        String role,

        @Schema(description = "When the user joined the conversation (UTC).", example = "2026-10-01T08:00:00")
        LocalDateTime joinedAt
) {

    public static ConversationMemberResponse from(ConversationMember member, String username, String displayName, String avatarObjectKey) {
        return new ConversationMemberResponse(
                member.getUserId(),
                username,
                displayName,
                avatarObjectKey,
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
