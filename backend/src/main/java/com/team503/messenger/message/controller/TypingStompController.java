package com.team503.messenger.message.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.TypingIndicator;
import com.team503.messenger.message.dto.TypingRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
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
 *       {@code /topic/conversations/{conversationId}/typing}. Subscribers in
 *       that conversation room receive the event in realtime.</li>
 * </ul>
 */
@Controller
public class TypingStompController {

    private static final Logger log = LoggerFactory.getLogger(TypingStompController.class);

    private final ConversationMemberRepository memberRepository;
    private final Clock clock;

    public TypingStompController(ConversationMemberRepository memberRepository, Clock clock) {
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @MessageMapping("/typing")
    @SendTo("/topic/conversations/{conversationId}/typing")
    public TypingIndicator typing(TypingRequest payload, SimpMessageHeaderAccessor headers) {
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

        return new TypingIndicator(
                payload.conversationId(),
                principal.id(),
                principal.username(),
                payload.typing(),
                LocalDateTime.now(clock)
        );
    }

    private static AuthenticatedUser extractPrincipal(SimpMessageHeaderAccessor headers) {
        Authentication auth = (Authentication) headers.getUser();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        return null;
    }
}
