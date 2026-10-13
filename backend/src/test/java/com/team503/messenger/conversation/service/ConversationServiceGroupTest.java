package com.team503.messenger.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team503.messenger.conversation.dto.AddMembersRequest;
import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.CreateGroupConversationRequest;
import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.file.service.ObjectStorageService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import com.team503.messenger.user.service.AvatarUrlResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationServiceGroupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 9, 0);

    private ConversationRepository conversations;
    private ConversationMemberRepository members;
    private UserRepository users;
    private ConversationService service;
    private final List<ConversationMember> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        members = mock(ConversationMemberRepository.class);
        users = mock(UserRepository.class);
        service = new ConversationService(
                conversations, members, users,
                new AvatarUrlResolver(mock(ObjectStorageService.class)),
                Clock.systemUTC());

        when(conversations.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.save(any(ConversationMember.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(members.findActiveByConversationId(any())).thenAnswer(inv -> List.copyOf(saved));
    }

    private User user(long id, boolean active) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getUsername()).thenReturn("u" + id);
        when(u.getDisplayName()).thenReturn("User " + id);
        when(u.isActive()).thenReturn(active);
        return u;
    }

    @Test
    void createGroupAddsCreatorAsOwnerAndOthersAsMembers() {
        User u0 = user(2, true);
        User u1 = user(3, true);
        when(users.findAllById(any())).thenReturn(List.of(u0, u1));

        ConversationDetailResponse res = service.createGroup(
                1L, new CreateGroupConversationRequest("  Study group ", List.of(2L, 3L, 2L, 1L)));

        assertThat(res.type()).isEqualTo("GROUP");
        assertThat(res.title()).isEqualTo("Study group");
        assertThat(saved).extracting(ConversationMember::getUserId).containsExactly(1L, 2L, 3L);
        assertThat(saved.get(0).getRole()).isEqualTo("OWNER");
        assertThat(saved.get(1).getRole()).isEqualTo("MEMBER");
    }

    @Test
    void blankTitleIsStoredAsNull() {
        User u0 = user(2, true);
        User u1 = user(3, true);
        when(users.findAllById(any())).thenReturn(List.of(u0, u1));

        ConversationDetailResponse res = service.createGroup(
                1L, new CreateGroupConversationRequest("   ", List.of(2L, 3L)));

        assertThat(res.title()).isNull();
    }

    @Test
    void groupNeedsAtLeastThreePeople() {
        assertThatThrownBy(() -> service.createGroup(1L, new CreateGroupConversationRequest(null, List.of(2L))))
                .isInstanceOf(ConversationException.class)
                .extracting("code").isEqualTo("GROUP_TOO_SMALL");
    }

    @Test
    void rejectsInactiveOrUnknownMembers() {
        User u0 = user(2, true);
        User u1 = user(3, false);
        when(users.findAllById(any())).thenReturn(List.of(u0, u1));

        assertThatThrownBy(() -> service.createGroup(1L, new CreateGroupConversationRequest(null, List.of(2L, 3L))))
                .isInstanceOf(ConversationException.class)
                .extracting("code").isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void cannotAddPeopleToADirectConversation() {
        Conversation direct = Conversation.createDirect(1L, 2L, 1L, "key", NOW);
        when(conversations.findById(10L)).thenReturn(Optional.of(direct));
        when(members.existsActiveMember(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.addMembers(1L, 10L, new AddMembersRequest(List.of(3L))))
                .isInstanceOf(ConversationException.class)
                .extracting("code").isEqualTo("NOT_A_GROUP");
    }

    @Test
    void onlyMembersCanAddPeople() {
        when(conversations.findById(10L)).thenReturn(Optional.of(Conversation.createGroup("g", 1L, NOW)));
        when(members.existsActiveMember(10L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.addMembers(9L, 10L, new AddMembersRequest(List.of(3L))))
                .isInstanceOf(ConversationException.class)
                .extracting("code").isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void addMembersSkipsExistingAndReactivatesFormerMembers() {
        Conversation group = Conversation.createGroup("g", 1L, NOW);
        when(conversations.findById(10L)).thenReturn(Optional.of(group));
        when(members.existsActiveMember(10L, 1L)).thenReturn(true);
        User u0 = user(2, true);
        User u1 = user(3, true);
        when(users.findAllById(any())).thenReturn(List.of(u0, u1));

        // 2 is already active; 3 left earlier and is brought back.
        ConversationMember active2 = ConversationMember.join(10L, 2L, NOW);
        ConversationMember former3 = ConversationMember.join(10L, 3L, NOW.minusDays(1));
        // The in-memory group has no id, so match any id.
        when(members.findActiveByConversationId(any())).thenReturn(List.of(active2));
        when(members.findByConversationIdAndUserId(any(), org.mockito.ArgumentMatchers.eq(3L)))
                .thenReturn(Optional.of(former3));

        List<Long> added = service.addMembersToGroup(group, List.of(2L, 3L), NOW);

        assertThat(added).containsExactly(3L);
        assertThat(former3.isActive()).isTrue();
        assertThat(former3.getJoinedAt()).isEqualTo(NOW); // rejoin() restamps the join time
    }
}
