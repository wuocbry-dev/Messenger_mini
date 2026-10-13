package com.team503.messenger.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class MessageRealtimePublisherTest {

    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private MessageRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        messagingTemplate = org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        publisher = new MessageRealtimePublisher(memberRepository, userRepository, messagingTemplate);
    }

    @Test
    void publishToConversationSendsMessageToAllActiveMembers() {
        ConversationMember sender = ConversationMember.joinAsOwner(10L, 1L, LocalDateTime.now());
        ConversationMember recipient = ConversationMember.join(10L, 2L, LocalDateTime.now());
        MessageResponse message = new MessageResponse(
                30L,
                10L,
                1L,
                "bob",
                "Bob",
                "IMAGE",
                null,
                LocalDateTime.now(),
                List.of(),
                List.of()
        );
        when(memberRepository.findActiveByConversationId(10L)).thenReturn(List.of(sender, recipient));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(activeUser(1L, "bob"), activeUser(2L, "alice")));

        publisher.publishToConversation(10L, message);

        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(users.capture(), eq("/queue/messages"), eq(message));
        assertThat(users.getAllValues()).containsExactlyInAnyOrder("bob", "alice");
    }

    private User activeUser(Long id, String username) {
        User user = User.register(username, username + "@example.com", "hash", username, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
