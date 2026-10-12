package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.Conversation;
import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        String type,
        String directKey,
        String title,
        String avatarObjectKey,
        LocalDateTime createdAt,
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
