package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.call.config.AgoraProperties;
import com.team503.messenger.call.dto.CallEvent;
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
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class CallServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private static final long CONV = 5L;
    private static final long CALLER = 1L;
    private static final long CALLEE = 2L;

    private CallRepository callRepository;
    private ConversationRepository conversationRepository;
    private ConversationMemberRepository memberRepository;
    private UserRepository userRepository;
    private AgoraTokenService tokenService;
    private SimpMessagingTemplate messaging;
    private AgoraProperties props;
    private CallService service;

    @BeforeEach
    void setUp() {
        callRepository = mock(CallRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        memberRepository = mock(ConversationMemberRepository.class);
        userRepository = mock(UserRepository.class);
        tokenService = mock(AgoraTokenService.class);
        messaging = mock(SimpMessagingTemplate.class);
        props = new AgoraProperties();
        props.setAppId("app-id");
        props.setAppCertificate("cert");
        props.setTokenTtlSeconds(600);
        props.setRingTimeoutSeconds(45);

        service = new CallService(callRepository, conversationRepository, memberRepository,
                userRepository, tokenService, props, messaging, CLOCK);

        when(conversationRepository.existsById(CONV)).thenReturn(true);
        when(memberRepository.existsActiveMember(eq(CONV), any())).thenReturn(true);
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> inv.getArgument(0));
        User caller = mock(User.class);
        when(caller.getId()).thenReturn(CALLER);
        when(caller.getUsername()).thenReturn("alice");
        User callee = mock(User.class);
        when(callee.getId()).thenReturn(CALLEE);
        when(callee.getUsername()).thenReturn("bob");
        when(userRepository.findAllById(any())).thenReturn(List.of(caller, callee));
    }

    private Call stored(Call.Status target) {
        Call call = Call.ringing(CONV, CALLER, CALLEE, Call.CallType.VOICE, "call-5-abc",
                LocalDateTime.now(CLOCK));
        if (target == Call.Status.ACCEPTED || target == Call.Status.ENDED) {
            call.markAccepted(LocalDateTime.now(CLOCK));
        }
        if (target == Call.Status.ENDED) {
            call.markEnded(CALLER, LocalDateTime.now(CLOCK));
        }
        if (target == Call.Status.REJECTED) {
            call.markRejected(LocalDateTime.now(CLOCK));
        }
        when(callRepository.findById(100L)).thenReturn(Optional.of(call));
        return call;
    }

    @Test
    void startCreatesRingingCallAndNotifiesBoth() {
        CallResponse res = service.startCall(CALLER, CONV, CALLEE);

        assertThat(res.status()).isEqualTo("RINGING");
        assertThat(res.agoraChannelName()).startsWith("call-5-");
        verify(messaging).convertAndSendToUser(eq("bob"), eq("/queue/calls"), any(CallEvent.class));
        verify(messaging).convertAndSendToUser(eq("alice"), eq("/queue/calls"), any(CallEvent.class));
    }

    @Test
    void startRejectsSelfCall() {
        assertThatThrownBy(() -> service.startCall(CALLER, CONV, CALLER))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALLER_AND_CALLEE_MUST_DIFFER");
    }

    @Test
    void startRejectsNonMember() {
        when(memberRepository.existsActiveMember(CONV, CALLEE)).thenReturn(false);
        assertThatThrownBy(() -> service.startCall(CALLER, CONV, CALLEE))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void startRejectsWhenConversationHasActiveCall() {
        when(callRepository.existsByConversationIdAndStatusIn(eq(CONV), any())).thenReturn(true);
        assertThatThrownBy(() -> service.startCall(CALLER, CONV, CALLEE))
                .extracting("code").isEqualTo("CALL_ALREADY_ACTIVE");
        verify(callRepository, never()).save(any());
    }

    @Test
    void startRejectsBusyCallee() {
        when(callRepository.existsActiveForUser(eq(CALLEE), any())).thenReturn(true);
        assertThatThrownBy(() -> service.startCall(CALLER, CONV, CALLEE))
                .extracting("code").isEqualTo("CALLEE_BUSY");
    }

    @Test
    void onlyCalleeCanAccept() {
        stored(Call.Status.RINGING);
        assertThatThrownBy(() -> service.acceptCall(CALLER, 100L))
                .extracting("code").isEqualTo("ONLY_CALLEE_CAN_ACCEPT");

        assertThat(service.acceptCall(CALLEE, 100L).status()).isEqualTo("ACCEPTED");
    }

    @Test
    void acceptFailsWhenNotRinging() {
        stored(Call.Status.REJECTED);
        assertThatThrownBy(() -> service.acceptCall(CALLEE, 100L))
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void rejectAndCancelRules() {
        stored(Call.Status.RINGING);
        assertThatThrownBy(() -> service.cancelCall(CALLEE, 100L))
                .extracting("code").isEqualTo("ONLY_CALLER_CAN_CANCEL");
        assertThat(service.cancelCall(CALLER, 100L).status()).isEqualTo("CANCELLED");

        stored(Call.Status.RINGING);
        assertThatThrownBy(() -> service.rejectCall(CALLER, 100L))
                .extracting("code").isEqualTo("ONLY_CALLEE_CAN_REJECT");
        assertThat(service.rejectCall(CALLEE, 100L).status()).isEqualTo("REJECTED");
    }

    @Test
    void endRequiresAcceptedAndParticipant() {
        stored(Call.Status.RINGING);
        assertThatThrownBy(() -> service.endCall(CALLER, 100L))
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");

        stored(Call.Status.ACCEPTED);
        assertThatThrownBy(() -> service.endCall(99L, 100L))
                .extracting("code").isEqualTo("NOT_A_PARTICIPANT");
        assertThat(service.endCall(CALLEE, 100L).status()).isEqualTo("ENDED");
    }

    @Test
    void callerGetsTokenWhileRingingCalleeOnlyAfterAccept() {
        when(tokenService.generateRtcToken("call-5-abc", CALLER, 600)).thenReturn("tok");
        stored(Call.Status.RINGING);

        CallTokenResponse t = service.issueToken(CALLER, 100L);
        assertThat(t.token()).isEqualTo("tok");
        assertThat(t.appId()).isEqualTo("app-id");
        assertThat(t.uid()).isEqualTo(CALLER);

        assertThatThrownBy(() -> service.issueToken(CALLEE, 100L))
                .extracting("code").isEqualTo("CALL_NOT_JOINABLE");
    }

    @Test
    void tokenRefusedForNonParticipantAndTerminalCall() {
        stored(Call.Status.ACCEPTED);
        assertThatThrownBy(() -> service.issueToken(99L, 100L))
                .extracting("code").isEqualTo("NOT_A_PARTICIPANT");

        stored(Call.Status.ENDED);
        assertThatThrownBy(() -> service.issueToken(CALLER, 100L))
                .extracting("code").isEqualTo("CALL_NOT_JOINABLE");
    }

    @Test
    void expireMarksStaleRingingCallsMissedAndNotifies() {
        Call stale = Call.ringing(CONV, CALLER, CALLEE, Call.CallType.VOICE, "c",
                LocalDateTime.now(CLOCK).minusSeconds(120));
        when(callRepository.findByStatusAndStartedAtBefore(eq(Call.Status.RINGING), any()))
                .thenReturn(List.of(stale));

        assertThat(service.expireStaleRinging()).isEqualTo(1);

        assertThat(stale.getStatus()).isEqualTo(Call.Status.MISSED);
        ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
        verify(messaging).convertAndSendToUser(eq("bob"), eq("/queue/calls"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("MISSED");
    }

    @Test
    void channelNamesAreUnique() {
        assertThat(AgoraChannelNameGenerator.generate(CONV))
                .isNotEqualTo(AgoraChannelNameGenerator.generate(CONV));
    }
}
