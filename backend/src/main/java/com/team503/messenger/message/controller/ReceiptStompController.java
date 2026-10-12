package com.team503.messenger.message.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MarkReceiptRequest;
import com.team503.messenger.message.dto.MessageReceiptUpdate;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.repository.MessageRepository;
import com.team503.messenger.message.service.ReceiptService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller for delivered/read receipt updates.
 *
 * <p>Wire contract:
 * <ul>
 *   <li>Client sends to {@code /app/receipts.mark} with
 *       {@link MarkReceiptRequest}.</li>
 *   <li>Server resolves the recipient from the authenticated principal —
 *       clients cannot impersonate other users.</li>
 *   <li>Server publishes {@link MessageReceiptUpdate} to each active
 *       conversation member's user queue at
 *       {@code /user/{username}/queue/receipts}. The sender sees the
 *       recipient's state change; the recipient sees their own state change
 *       for cross-device sync.</li>
 * </ul>
 */
@Controller
public class ReceiptStompController {

    private static final Logger log = LoggerFactory.getLogger(ReceiptStompController.class);

    private final ReceiptService receiptService;
    private final MessageRepository messageRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ReceiptStompController(
            ReceiptService receiptService,
            MessageRepository messageRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.receiptService = receiptService;
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/receipts.mark")
    public void mark(MarkReceiptRequest payload, SimpMessageHeaderAccessor headers) {
        AuthenticatedUser recipient = extractPrincipal(headers);
        if (recipient == null) {
            throw new IllegalStateException("Unauthenticated STOMP frame");
        }

        MessageReceiptUpdate.State state = payload.toState();
        if (state == null) {
            log.warn("Rejected receipts.mark: invalid state '{}'", payload.state());
            return;
        }

        Long messageId = payload.messageId();
        Long conversationId = messageRepository.findById(messageId)
                .map(m -> m.getConversationId())
                .orElse(null);
        if (conversationId == null) {
            log.warn("Rejected receipts.mark: messageId={} not found", messageId);
            return;
        }

        MessageReceipt receipt = (state == MessageReceiptUpdate.State.READ)
                ? receiptService.markRead(recipient.id(), messageId)
                : receiptService.markDelivered(recipient.id(), messageId);

        MessageReceiptUpdate update = receiptService.toUpdate(receipt, conversationId, state);
        broadcastToConversationMembers(conversationId, update);
    }

    private void broadcastToConversationMembers(Long conversationId, MessageReceiptUpdate update) {
        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversationId);
        Set<Long> memberIds = members.stream().map(ConversationMember::getUserId).collect(Collectors.toSet());
        Map<Long, String> usernames = userRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        for (String username : usernames.values()) {
            messagingTemplate.convertAndSendToUser(username, "/queue/receipts", update);
        }
    }

    private static AuthenticatedUser extractPrincipal(SimpMessageHeaderAccessor headers) {
        Authentication auth = (Authentication) headers.getUser();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        return null;
    }
}
