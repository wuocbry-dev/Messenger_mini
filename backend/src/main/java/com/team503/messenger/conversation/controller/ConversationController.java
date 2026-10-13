package com.team503.messenger.conversation.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.ConversationResponse;
import com.team503.messenger.conversation.dto.CreateDirectConversationRequest;
import com.team503.messenger.conversation.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Direct (1-to-1) conversations and listing.")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct")
    @Operation(
            summary = "Create or fetch a direct conversation with another user",
            description = "Returns the existing 1-to-1 conversation if one already exists."
    )
    ConversationDetailResponse createDirect(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        return conversationService.createOrGetDirectConversation(currentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "List conversations the current user participates in")
    List<ConversationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return conversationService.listConversations(currentUser.id());
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation detail (members, last message preview)")
    ConversationDetailResponse get(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId
    ) {
        return conversationService.getConversation(currentUser.id(), conversationId);
    }
}
