package com.team503.messenger.call.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InviteToCallRequest(
        @NotEmpty
        @Size(max = 7)
        List<Long> userIds
) {
}
