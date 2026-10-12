package com.team503.messenger.presence.event;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.presence.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Bridges STOMP session lifecycle to the {@link PresenceService}. We listen on
 * {@code SessionConnectedEvent} (after the CONNECT frame has been authenticated
 * by {@code StompAuthInterceptor}) and {@code SessionDisconnectEvent}.
 */
@Component
public class StompSessionPresenceListener {

    private static final Logger log = LoggerFactory.getLogger(StompSessionPresenceListener.class);

    private final PresenceService presenceService;

    public StompSessionPresenceListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        AuthenticatedUser principal = extractPrincipal(event.getMessage());
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        if (principal != null && sessionId != null) {
            log.debug("STOMP session connected: user={} session={}", principal.id(), sessionId);
            presenceService.sessionConnected(principal.id(), sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        AuthenticatedUser principal = extractPrincipal(event.getMessage());
        String sessionId = event.getSessionId();
        if (principal != null && sessionId != null) {
            log.debug("STOMP session disconnected: user={} session={}", principal.id(), sessionId);
            presenceService.sessionDisconnected(principal.id(), sessionId);
        }
    }

    private static AuthenticatedUser extractPrincipal(Message<?> message) {
        if (message == null) {
            return null;
        }
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        AuthenticatedUser fromUser = extractFromPrincipal(accessor.getUser());
        if (fromUser != null) {
            return fromUser;
        }
        Object simpUser = accessor.getHeader("simpUser");
        return extractFromPrincipal(simpUser);
    }

    private static AuthenticatedUser extractFromPrincipal(Object principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        if (principal instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
