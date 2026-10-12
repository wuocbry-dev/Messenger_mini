package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.Conversation;
import java.time.LocalDateTime;
import java.util.List;

public record ConversationDetailResponse(
        Long id,
        String type,
        String directKey,
        String title,
        String avatarObjectKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
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
