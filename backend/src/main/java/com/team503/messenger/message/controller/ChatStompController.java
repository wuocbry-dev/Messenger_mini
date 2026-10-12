package com.team503.messenger.message.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendChatMessageRequest;
import com.team503.messenger.message.service.MessageService;
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
 * STOMP controller for the 1-to-1 chat realtime slice.
 *
 * <p>Wire contract:
 * <ul>
 *   <li>Client sends to {@code /app/chat.send} with payload
 *       {@link SendChatMessageRequest}.</li>
 *   <li>The authenticated principal is read from the STOMP session (set by
 *       {@code WebSocketConfig.StompAuthInterceptor}). Client-supplied
 *       senderId is ignored by design — the principal is the only trusted
 *       source.</li>
 *   <li>Membership is verified server-side; non-members raise
 *       {@code MessageException} (logged) and no realtime event is published.</li>
 *   <li>Server publishes the persisted {@link MessageResponse} to each active
 *       conversation member's user queue at
 *       {@code /user/{username}/queue/messages} (via {@code SimpMessagingTemplate}).</li>
 * </ul>
 */
@Controller
public class ChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

    private final MessageService messageService;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatStompController(
            MessageService messageService,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messageService = messageService;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(SendChatMessageRequest payload, SimpMessageHeaderAccessor headers) {
        Authentication authentication = (Authentication) headers.getUser();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            log.warn("Rejected STOMP chat.send: no authenticated principal");
            throw new IllegalStateException("Unauthenticated STOMP frame");
        }
        // Server-controlled senderId. Client payload is intentionally ignored.
        Long senderId = principal.id();
        log.debug("STOMP chat.send from user={} conversation={}", senderId, payload.conversationId());

        MessageResponse response = messageService.sendChatMessage(
                senderId,
                payload.conversationId(),
                payload.toTypeOrDefault(),
                payload.content()
        );

        // Publish to all active members of the conversation so both the
        // sender (UI confirmation) and the recipient (live update) receive
        // the persisted payload.
        List<ConversationMember> members = memberRepository.findActiveByConversationId(payload.conversationId());
        Set<Long> memberIds = members.stream().map(ConversationMember::getUserId).collect(Collectors.toSet());

        Map<Long, String> usernames = userRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        for (ConversationMember member : members) {
            String username = usernames.get(member.getUserId());
            if (username != null) {
                messagingTemplate.convertAndSendToUser(
                        username,
                        "/queue/messages",
                        response
                );
            }
        }
    }
}
