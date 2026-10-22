package com.team503.messenger.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.entity.CallParticipant;
import com.team503.messenger.call.repository.CallParticipantRepository;
import com.team503.messenger.call.repository.CallRepository;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.message.dto.MessageCallResponse;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.repository.MessageReceiptRepository;
import com.team503.messenger.message.repository.MessageRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CallMessageServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T0 = LocalDateTime.now(CLOCK);
    private static final long CONV = 7L;
    private static final long ALICE = 1L;
    private static final long BOB = 2L;
    private static final long CAROL = 3L;

    private final List<CallParticipant> participants = new ArrayList<>();
    private final List<MessageReceipt> receipts = new ArrayList<>();

    private MessageRepository messageRepository;
    private MessageReceiptRepository receiptRepository;
    private CallRepository callRepository;
    private MessageRealtimePublisher publisher;
    private CallMessageService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(MessageRepository.class);
        receiptRepository = mock(MessageReceiptRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ConversationMemberRepository memberRepository = mock(ConversationMemberRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        callRepository = mock(CallRepository.class);
        CallParticipantRepository participantRepository = mock(CallParticipantRepository.class);
        publisher = mock(MessageRealtimePublisher.class);

        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 500L);
            return m;
        });
        when(receiptRepository.save(any(MessageReceipt.class))).thenAnswer(inv -> {
            receipts.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(receiptRepository.findByMessageId(anyLong())).thenAnswer(inv -> receipts);
        when(conversationRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(memberRepository.findActiveByConversationId(CONV)).thenReturn(List.of(
                ConversationMember.join(CONV, ALICE, T0),
                ConversationMember.join(CONV, BOB, T0),
                ConversationMember.join(CONV, CAROL, T0)));
        User alice = User.register("alice", "alice@example.test", "hash", "Alice", T0);
        ReflectionTestUtils.setField(alice, "id", ALICE);
        when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(participantRepository.findByCallIds(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return participants.stream().filter(p -> ids.contains(p.getCallId())).toList();
        });

        CallSummaryResolver resolver = new CallSummaryResolver(callRepository, participantRepository);
        service = new CallMessageService(messageRepository, receiptRepository, conversationRepository,
                memberRepository, userRepository, resolver, publisher, CLOCK);
    }

    private Call call(long id, Call.CallType type) {
        Call call = Call.ringing(CONV, ALICE, null, type, "ch-" + id, T0);
        ReflectionTestUtils.setField(call, "id", id);
        when(callRepository.findAllById(anyCollection())).thenReturn(List.of(call));
        participants.add(CallParticipant.caller(id, ALICE, T0));
        return call;
    }

    @Test
    void endedCallBecomesOneCallMessageWithDuration() {
        Call call = call(10L, Call.CallType.VIDEO);
        CallParticipant bob = CallParticipant.invited(10L, BOB, ALICE, T0);
        participants.add(bob);
        participants.add(CallParticipant.invited(10L, CAROL, ALICE, T0));
        call.markAccepted(T0.plusSeconds(5));
        bob.join(T0.plusSeconds(5));
        call.markEnded(BOB, T0.plusSeconds(200));

        service.recordFinishedCall(call);

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("CALL");
        assertThat(saved.getValue().getCallId()).isEqualTo(10L);
        assertThat(saved.getValue().getSenderId()).isEqualTo(ALICE);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(T0.plusSeconds(200));
        // Everyone but the caller gets a receipt, so the call counts as unread for them.
        assertThat(receipts).extracting(MessageReceipt::getUserId).containsExactlyInAnyOrder(BOB, CAROL);

        ArgumentCaptor<MessageResponse> published = ArgumentCaptor.forClass(MessageResponse.class);
        verify(publisher).publishToConversation(eq(CONV), published.capture());
        MessageCallResponse summary = published.getValue().call();
        assertThat(summary.callType()).isEqualTo("VIDEO");
        assertThat(summary.status()).isEqualTo("ENDED");
        assertThat(summary.durationSeconds()).isEqualTo(195L);
        assertThat(summary.groupCall()).isTrue();
        assertThat(summary.joinedCount()).isEqualTo(2);
    }

    @Test
    void unansweredCallHasNoDuration() {
        Call call = call(11L, Call.CallType.VOICE);
        participants.add(CallParticipant.invited(11L, BOB, ALICE, T0));
        call.markMissed(T0.plusSeconds(45));

        service.recordFinishedCall(call);

        ArgumentCaptor<MessageResponse> published = ArgumentCaptor.forClass(MessageResponse.class);
        verify(publisher).publishToConversation(eq(CONV), published.capture());
        assertThat(published.getValue().call().status()).isEqualTo("MISSED");
        assertThat(published.getValue().call().durationSeconds()).isNull();
        assertThat(published.getValue().call().groupCall()).isFalse();
    }

    @Test
    void callStillInProgressIsNotRecorded() {
        Call call = call(12L, Call.CallType.VOICE);

        service.recordFinishedCall(call);

        verify(messageRepository, never()).save(any());
        verify(publisher, never()).publishToConversation(anyLong(), any());
    }

    @Test
    void aCallIsRecordedOnlyOnce() {
        Call call = call(13L, Call.CallType.VOICE);
        call.markCancelled(T0.plusSeconds(3));
        when(messageRepository.existsByCallId(13L)).thenReturn(true);

        service.recordFinishedCall(call);

        verify(messageRepository, never()).save(any());
    }
}
