package com.team503.messenger.call.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.team503.messenger.call.entity.CallParticipant;
import com.team503.messenger.call.entity.CallParticipant.State;
import com.team503.messenger.call.exception.CallException;
import com.team503.messenger.call.repository.CallParticipantRepository;
import com.team503.messenger.call.repository.CallRepository;
import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.conversation.service.ConversationService;
import com.team503.messenger.file.service.ObjectStorageService;
import com.team503.messenger.message.service.CallMessageService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import com.team503.messenger.user.service.AvatarUrlResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises the call flows against small in-memory repositories so state
 * transitions across several participants can be verified end to end.
 */
class CallServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final long DIRECT_CONV = 5L;
    private static final long GROUP_CONV = 6L;
    private static final long ALICE = 1L;
    private static final long BOB = 2L;
    private static final long CAROL = 3L;
    private static final long DAVE = 4L;

    private final Map<Long, Call> calls = new ConcurrentHashMap<>();
    private final List<CallParticipant> participants = new ArrayList<>();
    private final AtomicLong callIds = new AtomicLong(100);

    private CallRepository callRepository;
    private CallParticipantRepository participantRepository;
    private ConversationRepository conversationRepository;
    private ConversationMemberRepository memberRepository;
    private ConversationService conversationService;
    private UserRepository userRepository;
    private AgoraTokenService tokenService;
    private SimpMessagingTemplate messaging;
    private CallMessageService callMessages;
    private AgoraProperties props;
    private CallService service;

    @BeforeEach
    void setUp() {
        callRepository = mock(CallRepository.class);
        participantRepository = mock(CallParticipantRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        memberRepository = mock(ConversationMemberRepository.class);
        conversationService = mock(ConversationService.class);
        userRepository = mock(UserRepository.class);
        tokenService = mock(AgoraTokenService.class);
        messaging = mock(SimpMessagingTemplate.class);
        callMessages = mock(CallMessageService.class);
        props = new AgoraProperties();
        props.setAppId("app-id");
        props.setAppCertificate("cert");
        props.setTokenTtlSeconds(600);
        props.setRingTimeoutSeconds(45);

        service = new CallService(callRepository, participantRepository, conversationRepository,
                memberRepository, conversationService, userRepository,
                new AvatarUrlResolver(mock(ObjectStorageService.class)), tokenService, props, messaging,
                callMessages, CLOCK);

        wireCallRepository();
        wireParticipantRepository();
        wireConversations();
        wireUsers();
    }

    // ---------- fakes ----------

    private void wireCallRepository() {
        when(callRepository.save(any(Call.class))).thenAnswer(inv -> {
            Call c = inv.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", callIds.incrementAndGet());
            }
            calls.put(c.getId(), c);
            return c;
        });
        when(callRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(calls.get(inv.<Long>getArgument(0))));
        when(callRepository.existsByConversationIdAndStatusIn(anyLong(), anyCollection())).thenAnswer(inv -> {
            Collection<Call.Status> statuses = inv.getArgument(1);
            return calls.values().stream().anyMatch(c ->
                    c.getConversationId().equals(inv.getArgument(0)) && statuses.contains(c.getStatus()));
        });
        when(callRepository.findByStatusAndStartedAtBefore(any(), any())).thenAnswer(inv -> {
            Call.Status status = inv.getArgument(0);
            LocalDateTime before = inv.getArgument(1);
            return calls.values().stream()
                    .filter(c -> c.getStatus() == status && c.getStartedAt().isBefore(before)).toList();
        });
    }

    private void wireParticipantRepository() {
        when(participantRepository.save(any(CallParticipant.class))).thenAnswer(inv -> {
            participants.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(participantRepository.findByCallId(anyLong())).thenAnswer(inv -> participants.stream()
                .filter(p -> p.getCallId().equals(inv.getArgument(0))).toList());
        when(participantRepository.findByCallIds(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return participants.stream().filter(p -> ids.contains(p.getCallId())).toList();
        });
        when(participantRepository.findByCallIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> participants.stream()
                .filter(p -> p.getCallId().equals(inv.getArgument(0)) && p.getUserId().equals(inv.getArgument(1)))
                .findFirst());
        when(participantRepository.existsBusy(anyLong(), anyCollection(), anyCollection())).thenAnswer(inv -> {
            Collection<State> states = inv.getArgument(1);
            Collection<Call.Status> statuses = inv.getArgument(2);
            return participants.stream().anyMatch(p -> p.getUserId().equals(inv.getArgument(0))
                    && states.contains(p.getState())
                    && statuses.contains(calls.get(p.getCallId()).getStatus()));
        });
        when(participantRepository.findStaleInvitations(any(), any(), any())).thenAnswer(inv -> {
            Call.Status callStatus = inv.getArgument(0);
            State state = inv.getArgument(1);
            LocalDateTime before = inv.getArgument(2);
            return participants.stream()
                    .filter(p -> p.getState() == state && p.getInvitedAt().isBefore(before)
                            && calls.get(p.getCallId()).getStatus() == callStatus)
                    .toList();
        });
    }

    private void wireConversations() {
        Conversation direct = Conversation.createDirect(ALICE, BOB, ALICE, "k", NOW);
        ReflectionTestUtils.setField(direct, "id", DIRECT_CONV);
        Conversation group = Conversation.createGroup("Team", ALICE, NOW);
        ReflectionTestUtils.setField(group, "id", GROUP_CONV);
        when(conversationRepository.findById(DIRECT_CONV)).thenReturn(Optional.of(direct));
        when(conversationRepository.findById(GROUP_CONV)).thenReturn(Optional.of(group));

        when(memberRepository.existsActiveMember(anyLong(), anyLong())).thenReturn(true);
        when(memberRepository.findActiveByConversationId(DIRECT_CONV))
                .thenReturn(members(DIRECT_CONV, ALICE, BOB));
        when(memberRepository.findActiveByConversationId(GROUP_CONV))
                .thenReturn(members(GROUP_CONV, ALICE, BOB, CAROL));
    }

    private List<ConversationMember> members(long conversationId, long... userIds) {
        List<ConversationMember> list = new ArrayList<>();
        for (long id : userIds) {
            list.add(ConversationMember.join(conversationId, id, NOW));
        }
        return list;
    }

    private void wireUsers() {
        when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<User> out = new ArrayList<>();
            for (Long id : ids) {
                out.add(user(id));
            }
            return out;
        });
    }

    private User user(long id) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getUsername()).thenReturn("user" + id);
        when(u.getDisplayName()).thenReturn("User " + id);
        when(u.isActive()).thenReturn(true);
        return u;
    }

    // ---------- helpers ----------

    private CallResponse startDirect() {
        return service.startCall(ALICE, DIRECT_CONV, BOB, Call.CallType.VOICE);
    }

    private CallResponse startGroup() {
        return service.startCall(ALICE, GROUP_CONV, null, Call.CallType.VOICE);
    }

    private State stateOf(long callId, long userId) {
        return participants.stream()
                .filter(p -> p.getCallId() == callId && p.getUserId() == userId)
                .findFirst().orElseThrow().getState();
    }

    /** Call events delivered to the given user so far. */
    private List<CallEvent> eventsTo(String username) {
        List<CallEvent> events = new ArrayList<>();
        for (var invocation : org.mockito.Mockito.mockingDetails(messaging).getInvocations()) {
            if (invocation.getMethod().getName().equals("convertAndSendToUser")
                    && username.equals(invocation.getArgument(0))
                    && invocation.getArgument(2) instanceof CallEvent event) {
                events.add(event);
            }
        }
        return events;
    }

    // ---------- 1-to-1 ----------

    @Test
    void directCallRingsTheOtherPersonOnly() {
        CallResponse res = startDirect();

        assertThat(res.status()).isEqualTo("RINGING");
        assertThat(res.calleeId()).isEqualTo(BOB);
        assertThat(res.agoraChannelName()).startsWith("call-5-");
        assertThat(res.groupCall()).isFalse();
        assertThat(stateOf(res.id(), ALICE)).isEqualTo(State.JOINED);
        assertThat(stateOf(res.id(), BOB)).isEqualTo(State.INVITED);

        assertThat(eventsTo("user2")).extracting(CallEvent::type).containsExactly("INCOMING");
        assertThat(eventsTo("user2").get(0).participants()).hasSize(2);
    }

    @Test
    void startRejectsSelfCall() {
        assertThatThrownBy(() -> service.startCall(ALICE, DIRECT_CONV, ALICE, Call.CallType.VOICE))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALLER_AND_CALLEE_MUST_DIFFER");
    }

    @Test
    void startRejectsNonMember() {
        when(memberRepository.existsActiveMember(DIRECT_CONV, ALICE)).thenReturn(false);

        assertThatThrownBy(startDirectAction())
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("NOT_A_MEMBER");
    }

    private org.assertj.core.api.ThrowableAssert.ThrowingCallable startDirectAction() {
        return () -> service.startCall(ALICE, DIRECT_CONV, BOB, Call.CallType.VOICE);
    }

    @Test
    void secondCallInSameConversationConflicts() {
        startDirect();

        assertThatThrownBy(startDirectAction())
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALL_ALREADY_ACTIVE");
    }

    @Test
    void busyUsersCannotStartOrReceiveAnotherCall() {
        startGroup(); // alice, bob, carol are now in a call

        assertThatThrownBy(startDirectAction())
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALLER_BUSY");
    }

    @Test
    void acceptJoinsTheInviteeAndStartsTheCall() {
        CallResponse started = startDirect();

        CallResponse accepted = service.acceptCall(BOB, started.id());

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(stateOf(started.id(), BOB)).isEqualTo(State.JOINED);
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("ACCEPTED");
    }

    @Test
    void onlyAnInviteeCanAccept() {
        CallResponse started = startDirect();

        assertThatThrownBy(() -> service.acceptCall(ALICE, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("ONLY_INVITEE_CAN_ACCEPT");
    }

    @Test
    void outsidersAreForbidden() {
        CallResponse started = startDirect();

        assertThatThrownBy(() -> service.acceptCall(99L, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("NOT_A_PARTICIPANT");
        assertThatThrownBy(() -> service.issueToken(99L, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("NOT_A_PARTICIPANT");
    }

    @Test
    void rejectEndsADirectCallAsRejected() {
        CallResponse started = startDirect();

        CallResponse rejected = service.rejectCall(BOB, started.id());

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(stateOf(started.id(), BOB)).isEqualTo(State.DECLINED);
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("REJECTED");
    }

    @Test
    void cancelMarksInviteesMissed() {
        CallResponse started = startDirect();

        CallResponse cancelled = service.cancelCall(ALICE, started.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(stateOf(started.id(), BOB)).isEqualTo(State.MISSED);
        assertThat(eventsTo("user2")).extracting(CallEvent::type).contains("CANCELLED");
    }

    @Test
    void onlyTheCallerCanCancel() {
        CallResponse started = startDirect();

        assertThatThrownBy(() -> service.cancelCall(BOB, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("ONLY_CALLER_CAN_CANCEL");
    }

    @Test
    void leavingATwoPersonCallEndsIt() {
        CallResponse started = startDirect();
        service.acceptCall(BOB, started.id());

        CallResponse ended = service.endCall(BOB, started.id());

        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(ended.endReason()).isEqualTo("ENDED_BY_CALLEE");
        assertThat(stateOf(started.id(), ALICE)).isEqualTo(State.LEFT);
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("ENDED");
    }

    @Test
    void endNeedsAnAcceptedCall() {
        CallResponse started = startDirect();

        assertThatThrownBy(() -> service.endCall(ALICE, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    // ---------- tokens ----------

    @Test
    void callerGetsATokenWhileRingingButInviteeOnlyAfterAccepting() {
        CallResponse started = startDirect();
        when(tokenService.generateRtcToken(any(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("tok");

        CallTokenResponse callerToken = service.issueToken(ALICE, started.id());
        assertThat(callerToken.token()).isEqualTo("tok");
        assertThat(callerToken.uid()).isEqualTo(ALICE);

        assertThatThrownBy(() -> service.issueToken(BOB, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALL_NOT_JOINABLE");

        service.acceptCall(BOB, started.id());
        assertThat(service.issueToken(BOB, started.id()).uid()).isEqualTo(BOB);
    }

    @Test
    void tokenRefusedAfterTheCallEnded() {
        CallResponse started = startDirect();
        service.cancelCall(ALICE, started.id());

        assertThatThrownBy(() -> service.issueToken(ALICE, started.id()))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALL_NOT_JOINABLE");
    }

    // ---------- group calls ----------

    @Test
    void groupCallRingsEveryOtherMember() {
        CallResponse res = startGroup();

        assertThat(res.calleeId()).isNull();
        assertThat(res.groupCall()).isTrue();
        assertThat(stateOf(res.id(), BOB)).isEqualTo(State.INVITED);
        assertThat(stateOf(res.id(), CAROL)).isEqualTo(State.INVITED);
        assertThat(eventsTo("user2")).extracting(CallEvent::type).containsExactly("INCOMING");
        assertThat(eventsTo("user3")).extracting(CallEvent::type).containsExactly("INCOMING");
        assertThat(eventsTo("user1")).isEmpty();
    }

    @Test
    void oneDeclineDoesNotEndAGroupCallButEveryDeclineDoes() {
        CallResponse started = startGroup();

        CallResponse afterBob = service.rejectCall(BOB, started.id());
        assertThat(afterBob.status()).isEqualTo("RINGING");
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("PARTICIPANT_DECLINED");

        CallResponse afterCarol = service.rejectCall(CAROL, started.id());
        assertThat(afterCarol.status()).isEqualTo("REJECTED");
    }

    @Test
    void groupCallContinuesUntilOnlyOnePersonRemains() {
        CallResponse started = startGroup();
        service.acceptCall(BOB, started.id());
        service.acceptCall(CAROL, started.id());
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("ACCEPTED", "PARTICIPANT_JOINED");

        CallResponse afterBobLeaves = service.endCall(BOB, started.id());
        assertThat(afterBobLeaves.status()).isEqualTo("ACCEPTED");
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("PARTICIPANT_LEFT");

        CallResponse afterCarolLeaves = service.endCall(CAROL, started.id());
        assertThat(afterCarolLeaves.status()).isEqualTo("ENDED");
        assertThat(stateOf(started.id(), ALICE)).isEqualTo(State.LEFT);
    }

    @Test
    void groupCallAboveTheLimitIsRejected() {
        List<ConversationMember> big = new ArrayList<>();
        for (long id = 1; id <= CallService.MAX_PARTICIPANTS + 1; id++) {
            big.add(ConversationMember.join(GROUP_CONV, id, NOW));
        }
        when(memberRepository.findActiveByConversationId(GROUP_CONV)).thenReturn(big);

        assertThatThrownBy(this::startGroup)
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALL_TOO_LARGE");
    }

    // ---------- inviting more people ----------

    @Test
    void invitingIntoADirectCallCreatesAGroupAndMovesTheCall() {
        CallResponse started = startDirect();
        service.acceptCall(BOB, started.id());
        Conversation created = Conversation.createGroup(null, ALICE, NOW);
        ReflectionTestUtils.setField(created, "id", 77L);
        when(conversationService.createGroupConversation(eq(ALICE), any(), anyCollection(), any()))
                .thenReturn(created);

        CallResponse res = service.invite(ALICE, started.id(), List.of(CAROL));

        assertThat(res.conversationId()).isEqualTo(77L);
        assertThat(stateOf(started.id(), CAROL)).isEqualTo(State.INVITED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> everyone = ArgumentCaptor.forClass(Collection.class);
        verify(conversationService).createGroupConversation(eq(ALICE), any(), everyone.capture(), any());
        assertThat(everyone.getValue()).containsExactlyInAnyOrder(ALICE, BOB, CAROL);

        assertThat(eventsTo("user3")).extracting(CallEvent::type).containsExactly("INCOMING");
        assertThat(eventsTo("user3").get(0).conversationId()).isEqualTo(77L);
        assertThat(eventsTo("user2")).extracting(CallEvent::type).contains("CONVERSATION_CHANGED");
    }

    @Test
    void invitingIntoAGroupCallAddsThemToThatGroup() {
        CallResponse started = startGroup();
        service.acceptCall(BOB, started.id());

        CallResponse res = service.invite(ALICE, started.id(), List.of(DAVE));

        assertThat(res.conversationId()).isEqualTo(GROUP_CONV);
        verify(conversationService).addMembersToGroup(any(Conversation.class), anyCollection(), any());
        verify(conversationService, never()).createGroupConversation(any(), any(), anyCollection(), any());
        assertThat(stateOf(started.id(), DAVE)).isEqualTo(State.INVITED);
        assertThat(eventsTo("user2")).extracting(CallEvent::type).contains("PARTICIPANT_INVITED");
    }

    @Test
    void invitedPersonCanAnswerAndJoinTheRunningCall() {
        CallResponse started = startGroup();
        service.acceptCall(BOB, started.id());
        service.invite(ALICE, started.id(), List.of(DAVE));

        CallResponse res = service.acceptCall(DAVE, started.id());

        assertThat(res.status()).isEqualTo("ACCEPTED");
        assertThat(stateOf(started.id(), DAVE)).isEqualTo(State.JOINED);
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("PARTICIPANT_JOINED");
    }

    @Test
    void onlyPeopleInTheCallCanInvite() {
        CallResponse started = startGroup();

        // Bob is still ringing, so he cannot invite yet.
        assertThatThrownBy(() -> service.invite(BOB, started.id(), List.of(DAVE)))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("NOT_IN_CALL");
    }

    @Test
    void cannotInviteSomeoneAlreadyInAnotherCall() {
        CallResponse started = startDirect();
        // Dave is busy in some other call.
        Call other = Call.ringing(99L, DAVE, null, Call.CallType.VOICE, "other", NOW);
        ReflectionTestUtils.setField(other, "id", 900L);
        calls.put(900L, other);
        participants.add(CallParticipant.caller(900L, DAVE, NOW));

        assertThatThrownBy(() -> service.invite(ALICE, started.id(), List.of(DAVE)))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALLEE_BUSY");
    }

    @Test
    void invitingPastTheLimitIsRejected() {
        CallResponse started = startGroup();
        List<Long> many = new ArrayList<>();
        for (long id = 10; id < 10 + CallService.MAX_PARTICIPANTS; id++) {
            many.add(id);
        }

        assertThatThrownBy(() -> service.invite(ALICE, started.id(), many))
                .isInstanceOf(CallException.class)
                .extracting("code").isEqualTo("CALL_TOO_LARGE");
    }

    // ---------- video upgrade ----------

    @Test
    void upgradingToVideoNotifiesEveryoneOnce() {
        CallResponse started = startDirect();
        service.acceptCall(BOB, started.id());

        CallResponse res = service.upgradeToVideo(BOB, started.id());
        assertThat(res.callType()).isEqualTo("VIDEO");
        assertThat(eventsTo("user1")).extracting(CallEvent::type).contains("MEDIA_UPGRADED");

        int before = eventsTo("user1").size();
        service.upgradeToVideo(ALICE, started.id()); // already video: no new event
        assertThat(eventsTo("user1")).hasSize(before);
    }

    // ---------- timeouts ----------

    @Test
    void unansweredRingingCallsBecomeMissed() {
        CallResponse started = startDirect();
        Clock later = Clock.fixed(CLOCK.instant().plusSeconds(60), ZoneOffset.UTC);
        CallService lateService = new CallService(callRepository, participantRepository, conversationRepository,
                memberRepository, conversationService, userRepository,
                new AvatarUrlResolver(mock(ObjectStorageService.class)), tokenService, props, messaging,
                callMessages, later);

        int expired = lateService.expireStaleRinging();

        assertThat(expired).isEqualTo(1);
        assertThat(calls.get(started.id()).getStatus()).isEqualTo(Call.Status.MISSED);
        assertThat(stateOf(started.id(), BOB)).isEqualTo(State.MISSED);
        assertThat(eventsTo("user2")).extracting(CallEvent::type).contains("MISSED");
        verify(callMessages).recordFinishedCall(calls.get(started.id()));
    }

    // ---------- chat timeline ----------

    @Test
    void endedCallIsRecordedInTheChat() {
        CallResponse started = startDirect();
        service.acceptCall(BOB, started.id());

        service.endCall(ALICE, started.id());

        verify(callMessages).recordFinishedCall(calls.get(started.id()));
    }

    @Test
    void rejectedAndCancelledCallsAreRecordedInTheChat() {
        CallResponse rejected = startDirect();
        service.rejectCall(BOB, rejected.id());
        CallResponse cancelled = startDirect();
        service.cancelCall(ALICE, cancelled.id());

        verify(callMessages).recordFinishedCall(calls.get(rejected.id()));
        verify(callMessages).recordFinishedCall(calls.get(cancelled.id()));
    }

    @Test
    void groupCallIsRecordedOnceWhenItEndsNotWhenSomeoneLeaves() {
        CallResponse started = startGroup();
        service.acceptCall(BOB, started.id());
        service.acceptCall(CAROL, started.id());

        service.endCall(BOB, started.id());
        verify(callMessages, never()).recordFinishedCall(any());

        service.endCall(CAROL, started.id());
        verify(callMessages).recordFinishedCall(calls.get(started.id()));
    }

    @Test
    void oneDeclineInAGroupIsNotRecordedYet() {
        CallResponse started = startGroup();

        service.rejectCall(BOB, started.id());

        verify(callMessages, never()).recordFinishedCall(any());
    }
}
