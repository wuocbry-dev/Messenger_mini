package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.call.config.AgoraProperties;
import com.team503.messenger.call.dto.CallHistoryResponse;
import com.team503.messenger.call.dto.CallResponse;
import com.team503.messenger.call.dto.CallTokenResponse;
import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.exception.CallException;
import com.team503.messenger.call.repository.CallRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class CallServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private CallRepository callRepository = mock(CallRepository.class);
    private ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private ConversationMemberRepository memberRepository = mock(ConversationMemberRepository.class);
    private UserRepository userRepository = mock(UserRepository.class);
    private AgoraTokenService agoraTokenService = mock(AgoraTokenService.class);
    private AgoraProperties agoraProperties;
    private SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private CallService callService;

    @BeforeEach
    void setUp() {
        agoraProperties = new AgoraProperties();
        agoraProperties.setAppId("test-app-id");
        agoraProperties.setAppCertificate("test-cert");
        agoraProperties.setTokenTtlSeconds(600);

        callService = new CallService(
                callRepository,
                conversationRepository,
                memberRepository,
                userRepository,
                agoraTokenService,
                agoraProperties,
                messagingTemplate,
                CLOCK
        );
    }

    @Test
    void startCallPersistsRingingCallAndPublishesIncoming() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        when(callRepository.findByConversationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> {
            Call c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 100L);
            return c;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.startCall(1L, 1L, 2L);

        assertThat(response.status()).isEqualTo("RINGING");
        assertThat(response.callerId()).isEqualTo(1L);
        assertThat(response.calleeId()).isEqualTo(2L);
        assertThat(response.agoraChannelName()).startsWith("call-1-");
        assertThat(response.callType()).isEqualTo("VOICE");

        // Callee (bob) and caller (alice) both receive the INCOMING event.
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void startCallRejectsSelfCall() {
        assertThatThrownBy(() -> callService.startCall(1L, 1L, 1L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("CALLER_AND_CALLEE_MUST_DIFFER");

        verify(callRepository, never()).save(any(Call.class));
    }

    @Test
    void startCallRejectsNonMember() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> callService.startCall(1L, 1L, 99L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void startCallRejectsWhenActiveCallExists() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        Call existing = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-old", NOW.minusMinutes(1));
        ReflectionTestUtils.setField(existing, "id", 99L);
        when(callRepository.findByConversationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing)));

        assertThatThrownBy(() -> callService.startCall(1L, 1L, 2L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("CALL_ALREADY_ACTIVE");
    }

    @Test
    void startCallClosesLeftoverAcceptedCallAndStartsNewOne() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        Call leftover = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-old", NOW.minusMinutes(5));
        ReflectionTestUtils.setField(leftover, "id", 99L);
        leftover.markAccepted(NOW.minusMinutes(4));
        when(callRepository.findByConversationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(leftover)));
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> {
            Call c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 100L);
            return c;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.startCall(1L, 1L, 2L);

        assertThat(leftover.getStatus()).isEqualTo(Call.Status.FAILED);
        assertThat(response.status()).isEqualTo("RINGING");
        // ENDED for the leftover call + INCOMING for the new one, each to both users.
        verify(messagingTemplate, times(4))
                .convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void acceptTransitionsRingingToAcceptedAndPublishes() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.acceptCall(2L, 100L);

        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.answeredAt()).isEqualTo(NOW);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void acceptRejectsCaller() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.acceptCall(1L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("ONLY_CALLEE_CAN_ACCEPT");
    }

    @Test
    void rejectTransitionsRingingToRejected() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.rejectCall(2L, 100L);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.endedBy()).isEqualTo(2L);
        assertThat(response.endReason()).isEqualTo("REJECTED_BY_CALLEE");
    }

    @Test
    void cancelRejectsNonCaller() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.cancelCall(2L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("ONLY_CALLER_CAN_CANCEL");
    }

    @Test
    void cancelTransitionsToCancelled() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.cancelCall(1L, 100L);

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void endRequiresAcceptedState() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.endCall(1L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void endTransitionsAcceptedToEnded() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        call.markAccepted(NOW.plusSeconds(1));
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.endCall(1L, 100L);

        assertThat(response.status()).isEqualTo("ENDED");
        assertThat(response.endedBy()).isEqualTo(1L);
        assertThat(response.endReason()).isEqualTo("ENDED_BY_CALLER");
    }

    @Test
    void endRejectsNonParticipant() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        call.markAccepted(NOW.plusSeconds(1));
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> callService.endCall(99L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("NOT_A_PARTICIPANT");
    }

    @Test
    void issueTokenReturnsAgoraTokenForParticipant() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(agoraTokenService.generateRtcToken(anyString(), anyLong(), anyInt()))
                .thenReturn("agora-token-xyz");

        CallTokenResponse token = callService.issueToken(1L, 100L);

        assertThat(token.callId()).isEqualTo(100L);
        assertThat(token.agoraChannelName()).isEqualTo("call-1-x");
        assertThat(token.token()).isEqualTo("agora-token-xyz");
        assertThat(token.appId()).isEqualTo("test-app-id");
        // App certificate must never appear in the token response.
        assertThat(token.token()).doesNotContain("test-cert");
        assertThat(token.appId()).isEqualTo("test-app-id");
    }

    @Test
    void issueTokenRejectsNonParticipant() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(memberRepository.existsActiveMember(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> callService.issueToken(99L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("NOT_A_PARTICIPANT");

        verify(agoraTokenService, never()).generateRtcToken(anyString(), anyLong(), anyInt());
    }

    @Test
    void issueTokenRefusesTerminalCall() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        call.markRejected(NOW.plusSeconds(1));
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> callService.issueToken(1L, 100L))
                .isInstanceOf(CallException.class)
                .extracting(e -> ((CallException) e).getCode())
                .isEqualTo("CALL_NOT_JOINABLE");
    }

    @Test
    void listHistoryReturnsCallsForUser() {
        Call c1 = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-a", NOW);
        ReflectionTestUtils.setField(c1, "id", 1L);
        c1.markAccepted(NOW.plusSeconds(1));
        c1.markEnded(1L, Call.EndReason.ENDED_BY_CALLER, NOW.plusSeconds(30));

        when(callRepository.findByUserIdOrderByStartedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1)));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallHistoryResponse response = callService.listHistory(1L, 0, 20);

        assertThat(response.calls()).hasSize(1);
        assertThat(response.calls().get(0).status()).isEqualTo("ENDED");
    }

    @Test
    void getCallReturnsCallForParticipant() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-x", NOW);
        ReflectionTestUtils.setField(call, "id", 100L);
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse response = callService.getCall(1L, 100L);

        assertThat(response.id()).isEqualTo(100L);
    }

    @Test
    void startCallGeneratesUniqueChannelNameEachTime() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 1L)).thenReturn(true);
        when(memberRepository.existsActiveMember(1L, 2L)).thenReturn(true);
        when(callRepository.findByConversationIdOrderByStartedAtDesc(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of(activeUser(1L, "alice"), activeUser(2L, "bob")));

        CallResponse a = callService.startCall(1L, 1L, 2L);
        CallResponse b = callService.startCall(1L, 1L, 2L);

        // Despite the same conversation, the channel name embeds a random
        // token; two consecutive calls should never share a name.
        assertThat(a.agoraChannelName()).isNotEqualTo(b.agoraChannelName());
    }

    private User activeUser(Long id, String username) {
        User user = User.register(username, username + "@example.com", "hash", username, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
