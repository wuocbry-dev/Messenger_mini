package com.team503.messenger.message.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.message.dto.MessagePageResponse;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendAttachmentRequest;
import com.team503.messenger.message.dto.SendMessageRequest;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/conversations/{conversationId}/messages")
    MessageResponse sendMessage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return messageService.sendMessage(currentUser.id(), conversationId, request);
    }

    @PostMapping("/conversations/{conversationId}/attachments")
    MessageResponse sendAttachment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendAttachmentRequest request
    ) {
        if (!request.conversationId().equals(conversationId)) {
            throw new MessageException(
                    HttpStatus.BAD_REQUEST,
                    "CONVERSATION_MISMATCH",
                    "conversationId in path and body must match"
            );
        }
        Message.Type type = request.toType();
        if (type == null) {
            throw new MessageException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TYPE",
                    "type must be IMAGE or FILE"
            );
        }
        return messageService.sendAttachmentMessage(
                currentUser.id(),
                conversationId,
                request.fileId(),
                type,
                request.content()
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    MessagePageResponse listMessages(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return messageService.listMessages(currentUser.id(), conversationId, page, size);
    }

    @GetMapping("/messages/{messageId}")
    MessageResponse getMessage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long messageId
    ) {
        return messageService.getMessage(currentUser.id(), messageId);
    }
}
