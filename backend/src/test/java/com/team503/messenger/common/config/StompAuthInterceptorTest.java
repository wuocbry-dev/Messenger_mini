package com.team503.messenger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.auth.security.JwtService;
import com.team503.messenger.auth.security.JwtValidationException;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class StompAuthInterceptorTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private MessageChannel channel;
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = org.mockito.Mockito.mock(JwtService.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        channel = org.mockito.Mockito.mock(MessageChannel.class);
        interceptor = new StompAuthInterceptor(jwtService, userRepository);
    }

    @Test
    void preSendConnectAuthenticatesAndSetsPrincipal() {
        User user = activeUser(1L, "bob");
        when(jwtService.parseAccessToken("valid-token"))
                .thenReturn(new AuthenticatedUser(1L, "bob"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor returned = StompHeaderAccessor.wrap(result);
        Authentication authentication = (Authentication) returned.getUser();
        assertThat(authentication).isNotNull();
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(principal.id()).isEqualTo(1L);
        assertThat(principal.username()).isEqualTo("bob");
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        // convertAndSendToUser(username, ...) routes by this name.
        assertThat(authentication.getName()).isEqualTo("bob");
    }

    @Test
    void preSendConnectRejectsMissingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Missing Bearer token");
    }

    @Test
    void preSendConnectRejectsInvalidToken() {
        when(jwtService.parseAccessToken("bad-token"))
                .thenThrow(new JwtValidationException("Invalid token signature"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Invalid STOMP token");
    }

    @Test
    void preSendConnectRejectsInactiveUser() {
        when(jwtService.parseAccessToken("valid-token"))
                .thenReturn(new AuthenticatedUser(1L, "bob"));
        User blocked = activeUser(1L, "bob");
        ReflectionTestUtils.setField(blocked, "status", "BLOCKED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocked));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void preSendNonConnectFramePassesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat.send");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
    }

    private User activeUser(Long id, String username) {
        User user = User.register(username, username + "@example.com", "hash", username, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
