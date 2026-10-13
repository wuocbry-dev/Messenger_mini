package com.team503.messenger.conversation.dto;

import com.team503.messenger.conversation.entity.ConversationMember;
import java.time.LocalDateTime;

public record ConversationMemberResponse(
        Long userId,
        String username,
        String displayName,
        String avatarObjectKey,
        String avatarUrl,
        String role,
        LocalDateTime joinedAt
) {

    public static ConversationMemberResponse from(
            ConversationMember member,
            String username,
            String displayName,
            String avatarObjectKey,
            String avatarUrl
    ) {
        return new ConversationMemberResponse(
                member.getUserId(),
                username,
                displayName,
                avatarObjectKey,
                avatarUrl,
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
