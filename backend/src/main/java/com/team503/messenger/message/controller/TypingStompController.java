package com.team503.messenger.message.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.TypingIndicator;
import com.team503.messenger.message.dto.TypingRequest;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP controller for typing indicators. Typing is realtime-only and is never
 * persisted to MySQL.
 *
 * <p>Wire contract:
 * <ul>
 *   <li>Client sends to {@code /app/typing} with {@link TypingRequest}
 *       ({@code conversationId}, {@code typing}).</li>
 *   <li>Server sets {@code senderId} from the authenticated principal.</li>
 *   <li>Server validates that the sender is an active member of the
 *       conversation.</li>
 *   <li>Server broadcasts a {@link TypingIndicator} to
 *       {@code /user/{recipient}/queue/typing} so the recipient receives it
 *       on their personal queue (matching the frontend subscription).</li>
 *   <li>Note: only the <em>other</em> participant receives the event —
 *       the sender already knows they are typing.</li>
 * </ul>
 */
@Controller
public class TypingStompController {

    private static final Logger log = LoggerFactory.getLogger(TypingStompController.class);

    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public TypingStompController(
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @MessageMapping("/typing")
    public void typing(TypingRequest payload, SimpMessageHeaderAccessor headers) {
        AuthenticatedUser principal = extractPrincipal(headers);
        if (principal == null) {
            throw new IllegalStateException("Unauthenticated STOMP frame");
        }

        if (!memberRepository.existsActiveMember(payload.conversationId(), principal.id())) {
            throw new ConversationException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        log.debug("Typing indicator: user={} conversation={} typing={}",
                principal.id(), payload.conversationId(), payload.typing());

        TypingIndicator indicator = new TypingIndicator(
                payload.conversationId(),
                principal.id(),
                principal.username(),
                payload.typing(),
                LocalDateTime.now(clock)
        );

        // Deliver only to the other active members' personal queues. A shared
        // topic would let any client subscribe to any conversation's typing.
        Set<Long> recipientIds = memberRepository.findActiveByConversationId(payload.conversationId()).stream()
                .map(ConversationMember::getUserId)
                .filter(id -> !id.equals(principal.id()))
                .collect(Collectors.toSet());
        for (User recipient : userRepository.findAllById(recipientIds)) {
            messagingTemplate.convertAndSendToUser(recipient.getUsername(), "/queue/typing", indicator);
        }
    }

    private static AuthenticatedUser extractPrincipal(SimpMessageHeaderAccessor headers) {
        Object user = headers.getUser();
        // getUser() returns the Authentication object set by StompAuthInterceptor
        if (user instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AuthenticatedUser au) {
                return au;
            }
        }
        return null;
    }
}
