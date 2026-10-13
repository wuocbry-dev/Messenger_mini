package com.team503.messenger.call.dto;

import com.team503.messenger.call.entity.CallParticipant;
import com.team503.messenger.user.entity.User;

/** One person in a call, with enough profile data for the UI to render them. */
public record CallParticipantResponse(
        Long userId,
        String username,
        String displayName,
        String avatarUrl,
        String state
) {

    public static CallParticipantResponse of(CallParticipant participant, User user, String avatarUrl) {
        return new CallParticipantResponse(
                participant.getUserId(),
                user != null ? user.getUsername() : "?",
                user != null ? user.getDisplayName() : "?",
                avatarUrl,
                participant.getState().name()
        );
    }
}
