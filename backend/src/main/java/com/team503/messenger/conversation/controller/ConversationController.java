package com.team503.messenger.conversation.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.ConversationResponse;
import com.team503.messenger.conversation.dto.CreateDirectConversationRequest;
import com.team503.messenger.conversation.service.ConversationService;
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
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct")
    ConversationDetailResponse createDirect(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        return conversationService.createOrGetDirectConversation(currentUser.id(), request);
    }

    @GetMapping
    List<ConversationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return conversationService.listConversations(currentUser.id());
    }

    @GetMapping("/{conversationId}")
    ConversationDetailResponse get(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId
    ) {
        return conversationService.getConversation(currentUser.id(), conversationId);
    }
}
