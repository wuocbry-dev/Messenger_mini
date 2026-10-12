package com.team503.messenger.common.config;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.auth.security.JwtService;
import com.team503.messenger.auth.security.JwtValidationException;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Extracted from {@link WebSocketConfig} so it can be tested directly without
 * reflection. Spring picks up the same bean via the package-private constructor
 * inside {@link WebSocketConfig}.
 */
class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    StompAuthInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new JwtValidationException("Missing Bearer token in STOMP CONNECT");
            }
            String token = auth.substring(7);
            AuthenticatedUser tokenUser;
            try {
                tokenUser = jwtService.parseAccessToken(token);
            } catch (JwtValidationException e) {
                throw new JwtValidationException("Invalid STOMP token: " + e.getMessage());
            }

            Optional<User> user = userRepository.findById(tokenUser.id())
                    .filter(User::isActive);
            if (user.isEmpty()) {
                throw new JwtValidationException("STOMP user is not active");
            }

            AuthenticatedUser principal = new AuthenticatedUser(user.get().getId(), user.get().getUsername());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            accessor.setUser(authentication);
        }
        return message;
    }
}
