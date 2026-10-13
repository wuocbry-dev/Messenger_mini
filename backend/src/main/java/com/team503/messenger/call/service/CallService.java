package com.team503.messenger.call.service;

import com.team503.messenger.call.config.AgoraProperties;
import com.team503.messenger.call.dto.CallEvent;
import com.team503.messenger.call.dto.CallEvent.EventType;
import com.team503.messenger.call.dto.CallHistoryResponse;
import com.team503.messenger.call.dto.CallResponse;
import com.team503.messenger.call.dto.CallTokenResponse;
import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.exception.CallException;
import com.team503.messenger.call.repository.CallRepository;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Voice call business logic. Agora carries the media; this service owns
 * authorization, call state, channel naming, token issuance and history.
 */
@Service
public class CallService {

    private static final Set<Call.Status> ACTIVE = EnumSet.of(Call.Status.RINGING, Call.Status.ACCEPTED);

    private final CallRepository callRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AgoraTokenService agoraTokenService;
    private final AgoraProperties agoraProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public CallService(
            CallRepository callRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            AgoraTokenService agoraTokenService,
            AgoraProperties agoraProperties,
            SimpMessagingTemplate messagingTemplate,
            Clock clock
    ) {
        this.callRepository = callRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.agoraTokenService = agoraTokenService;
        this.agoraProperties = agoraProperties;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @Transactional
    public CallResponse startCall(Long callerId, Long conversationId, Long calleeId, Call.CallType callType) {
        if (callerId.equals(calleeId)) {
            throw new CallException(HttpStatus.BAD_REQUEST, "CALLER_AND_CALLEE_MUST_DIFFER",
                    "Caller and callee must be different users");
        }
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                    "Conversation not found");
        }
        requireMember(conversationId, callerId, "caller");
        requireMember(conversationId, calleeId, "callee");

        if (callRepository.existsByConversationIdAndStatusIn(conversationId, ACTIVE)) {
            throw new CallException(HttpStatus.CONFLICT, "CALL_ALREADY_ACTIVE",
                    "A call is already active in this conversation");
        }
        if (callRepository.existsActiveForUser(callerId, ACTIVE)) {
            throw new CallException(HttpStatus.CONFLICT, "CALLER_BUSY", "You are already in a call");
        }
        if (callRepository.existsActiveForUser(calleeId, ACTIVE)) {
            throw new CallException(HttpStatus.CONFLICT, "CALLEE_BUSY", "The other user is already in a call");
        }

        Call call = callRepository.save(Call.ringing(
                conversationId,
                callerId,
                calleeId,
                callType,
                AgoraChannelNameGenerator.generate(conversationId),
                LocalDateTime.now(clock)
        ));
        publish(EventType.INCOMING, call, true);
        return toResponse(call);
    }

    @Transactional
    public CallResponse acceptCall(Long userId, Long callId) {
        Call call = loadForParticipant(callId, userId);
        requireCallee(call, userId, "ONLY_CALLEE_CAN_ACCEPT", "Only the callee can accept the call");
        requireRinging(call);
        call.markAccepted(LocalDateTime.now(clock));
        publish(EventType.ACCEPTED, call, true);
        return toResponse(call);
    }

    @Transactional
    public CallResponse rejectCall(Long userId, Long callId) {
        Call call = loadForParticipant(callId, userId);
        requireCallee(call, userId, "ONLY_CALLEE_CAN_REJECT", "Only the callee can reject the call");
        requireRinging(call);
        call.markRejected(LocalDateTime.now(clock));
        publish(EventType.REJECTED, call, true);
        return toResponse(call);
    }

    @Transactional
    public CallResponse cancelCall(Long userId, Long callId) {
        Call call = loadForParticipant(callId, userId);
        if (!call.getCallerId().equals(userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, "ONLY_CALLER_CAN_CANCEL",
                    "Only the caller can cancel the call");
        }
        requireRinging(call);
        call.markCancelled(LocalDateTime.now(clock));
        publish(EventType.CANCELLED, call, false);
        return toResponse(call);
    }

    @Transactional
    public CallResponse endCall(Long userId, Long callId) {
        Call call = loadForParticipant(callId, userId);
        if (call.getStatus() != Call.Status.ACCEPTED) {
            throw new CallException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                    "Call must be ACCEPTED to be ended");
        }
        call.markEnded(userId, LocalDateTime.now(clock));
        publish(EventType.ENDED, call, true);
        return toResponse(call);
    }

    /**
     * Issues a temporary RTC token. The caller may join while the call is
     * ringing or accepted; the callee only after accepting. The uid is the
     * user's numeric id and the token is bound to it. Tokens are not stored.
     */
    @Transactional(readOnly = true)
    public CallTokenResponse issueToken(Long userId, Long callId) {
        Call call = loadForParticipant(callId, userId);
        boolean isCaller = call.getCallerId().equals(userId);
        boolean joinable = call.getStatus() == Call.Status.ACCEPTED
                || (isCaller && call.getStatus() == Call.Status.RINGING);
        if (!joinable) {
            throw new CallException(HttpStatus.CONFLICT, "CALL_NOT_JOINABLE",
                    "Call is not in a state where a token can be issued");
        }

        int ttl = Math.toIntExact(agoraProperties.getTokenTtlSeconds());
        String token = agoraTokenService.generateRtcToken(call.getAgoraChannelName(), userId, ttl);
        return new CallTokenResponse(call.getId(), call.getAgoraChannelName(), token,
                agoraProperties.getAppId(), userId, ttl);
    }

    /** Marks calls that rang past the timeout as MISSED and notifies both sides. */
    @Transactional
    public int expireStaleRinging() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(agoraProperties.getRingTimeoutSeconds());
        List<Call> stale = callRepository.findByStatusAndStartedAtBefore(Call.Status.RINGING, threshold);
        LocalDateTime now = LocalDateTime.now(clock);
        for (Call call : stale) {
            call.markMissed(now);
            publish(EventType.MISSED, call, true);
        }
        return stale.size();
    }

    @Transactional(readOnly = true)
    public CallHistoryResponse listHistory(Long userId, int page, int size) {
        return toHistoryResponse(callRepository.findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public CallHistoryResponse listConversationHistory(Long userId, Long conversationId, int page, int size) {
        requireMember(conversationId, userId, "user");
        return toHistoryResponse(
                callRepository.findByConversationIdOrderByStartedAtDesc(conversationId, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public CallResponse getCall(Long userId, Long callId) {
        return toResponse(loadForParticipant(callId, userId));
    }

    // ---------- helpers ----------

    private void requireMember(Long conversationId, Long userId, String role) {
        if (!memberRepository.existsActiveMember(conversationId, userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
                    "User is not an active member of this conversation (" + role + ")");
        }
    }

    private void requireCallee(Call call, Long userId, String code, String message) {
        if (!call.getCalleeId().equals(userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, code, message);
        }
    }

    private void requireRinging(Call call) {
        if (call.getStatus() != Call.Status.RINGING) {
            throw new CallException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                    "Call is not in RINGING state");
        }
    }

    /** Only the two participants may see or act on a call. */
    private Call loadForParticipant(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", "Call not found"));
        if (!call.isParticipant(userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT",
                    "You are not a participant in this call");
        }
        return call;
    }

    /** Publishes after the transaction commits so clients never see uncommitted state. */
    private void publish(EventType type, Call call, boolean alsoCaller) {
        CallEvent event = CallEvent.of(type, call);
        Runnable send = () -> {
            Map<Long, String> names = usernames(Set.of(call.getCallerId(), call.getCalleeId()));
            sendTo(names.get(call.getCalleeId()), event);
            if (alsoCaller) {
                sendTo(names.get(call.getCallerId()), event);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void sendTo(String username, CallEvent event) {
        if (username != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/calls", event);
        }
    }

    private Map<Long, String> usernames(Set<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private CallResponse toResponse(Call call) {
        Map<Long, String> names = usernames(Set.of(call.getCallerId(), call.getCalleeId()));
        return CallResponse.from(call,
                names.getOrDefault(call.getCallerId(), "?"),
                names.getOrDefault(call.getCalleeId(), "?"));
    }

    private CallHistoryResponse toHistoryResponse(Page<Call> page) {
        List<Call> calls = page.getContent();
        Set<Long> ids = calls.stream()
                .flatMap(c -> Stream.of(c.getCallerId(), c.getCalleeId()))
                .collect(Collectors.toSet());
        Map<Long, String> names = usernames(ids);
        List<CallResponse> responses = calls.stream()
                .map(c -> CallResponse.from(c,
                        names.getOrDefault(c.getCallerId(), "?"),
                        names.getOrDefault(c.getCalleeId(), "?")))
                .toList();
        return new CallHistoryResponse(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
