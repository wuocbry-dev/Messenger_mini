package com.team503.messenger.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.ConversationResponse;
import com.team503.messenger.conversation.dto.CreateDirectConversationRequest;
import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ConversationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private ConversationRepository conversationRepository;
    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationRepository = org.mockito.Mockito.mock(ConversationRepository.class);
        memberRepository = org.mockito.Mockito.mock(ConversationMemberRepository.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        conversationService = new ConversationService(
                conversationRepository,
                memberRepository,
                userRepository,
                CLOCK
        );
    }

    // --- createOrGetDirectConversation ---

    @Test
    void createDirectConversationCreatesNewConversationAndMembers() {
        User targetUser = activeUser(2L, "alice", "alice@example.com", "Alice");
        User currentUser = activeUser(1L, "bob", "bob@example.com", "Bob");
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(conversationRepository.findByDirectKey(any())).thenReturn(Optional.empty());

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 1L);
            return c;
        });

        when(userRepository.findAllById(any())).thenReturn(List.of(currentUser, targetUser));

        ConversationDetailResponse response = conversationService.createOrGetDirectConversation(
                1L, new CreateDirectConversationRequest(2L)
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo("DIRECT");
        assertThat(response.members()).hasSize(2);

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertThat(convCaptor.getValue().getType()).isEqualTo("DIRECT");

        verify(memberRepository, org.mockito.Mockito.times(2)).save(any(ConversationMember.class));
    }

    @Test
    void createDirectConversationReturnsExistingIfAlreadyExists() {
        User targetUser = activeUser(2L, "alice", "alice@example.com", "Alice");
        User currentUser = activeUser(1L, "bob", "bob@example.com", "Bob");
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        Conversation existing = createConv(5L, "key-1-2");
        ConversationMember m1 = ConversationMember.joinAsOwner(5L, 1L, NOW);
        ConversationMember m2 = ConversationMember.join(5L, 2L, NOW);

        when(conversationRepository.findByDirectKey(any())).thenReturn(Optional.of(existing));
        when(memberRepository.findActiveByConversationId(5L)).thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(any())).thenReturn(List.of(currentUser, targetUser));

        ConversationDetailResponse response = conversationService.createOrGetDirectConversation(
                1L, new CreateDirectConversationRequest(2L)
        );

        assertThat(response.id()).isEqualTo(5L);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void createDirectConversationRejectsSelf() {
        assertThatThrownBy(() -> conversationService.createOrGetDirectConversation(
                1L, new CreateDirectConversationRequest(1L)
        ))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("SELF_CONVERSATION");
    }

    @Test
    void createDirectConversationRejectsMissingUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.createOrGetDirectConversation(
                1L, new CreateDirectConversationRequest(999L)
        ))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void createDirectConversationRejectsInactiveUser() {
        User inactive = inactiveUser(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> conversationService.createOrGetDirectConversation(
                1L, new CreateDirectConversationRequest(2L)
        ))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    // --- listConversations ---

    @Test
    void listConversationsReturnsOnlyCurrentUserMemberships() {
        Conversation c1 = createConv(1L, "key-1-2");
        Conversation c2 = createConv(2L, "key-1-3");
        when(conversationRepository.findActiveByMemberUserId(1L)).thenReturn(List.of(c1, c2));

        List<ConversationResponse> result = conversationService.listConversations(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    // --- getConversation ---

    @Test
    void getConversationReturnsDetailForMember() {
        User user1 = activeUser(1L, "bob", "bob@example.com", "Bob");
        User user2 = activeUser(2L, "alice", "alice@example.com", "Alice");

        Conversation conv = createConv(1L, "key-1-2");
        ConversationMember m1 = ConversationMember.joinAsOwner(1L, 1L, NOW);
        ConversationMember m2 = ConversationMember.join(1L, 2L, NOW);

        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.findActiveByConversationId(1L)).thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        ConversationDetailResponse response = conversationService.getConversation(1L, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.members()).hasSize(2);
    }

    @Test
    void getConversationRejectsNonMember() {
        Conversation conv = createConv(1L, "key-2-3");
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> conversationService.getConversation(99L, 1L))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void getConversationRejectsNotFound() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversation(1L, 999L))
                .isInstanceOf(ConversationException.class)
                .extracting(e -> ((ConversationException) e).getCode())
                .isEqualTo("CONVERSATION_NOT_FOUND");
    }

    // --- helpers ---

    private User activeUser(Long id, String username, String email, String displayName) {
        User user = User.register(username, email, "hash", displayName, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User inactiveUser(Long id) {
        User user = User.register("user", "user@example.com", "hash", "User", NOW);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", "BLOCKED");
        return user;
    }

    private Conversation createConv(Long id, String directKey) {
        Conversation conv = Conversation.createDirect(1L, 2L, 1L, directKey, NOW);
        ReflectionTestUtils.setField(conv, "id", id);
        return conv;
    }
}
