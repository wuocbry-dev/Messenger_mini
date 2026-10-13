package com.team503.messenger.conversation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddMembersRequest(
        @NotEmpty
        @Size(max = 49)
        List<Long> userIds
) {
}
