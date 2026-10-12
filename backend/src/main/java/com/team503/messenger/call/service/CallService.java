package com.team503.messenger.call.service;

import com.team503.messenger.call.config.AgoraProperties;
import com.team503.messenger.call.dto.CallEvent;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Voice call business logic. Agora handles media transport; this service
 * owns authorization, call state, channel naming, token generation, and
 * history.
 */
@Service
public class CallService {

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
    public CallResponse startCall(Long callerId, Long conversationId, Long calleeId) {
        if (callerId.equals(calleeId)) {
            throw new CallException(
                    HttpStatus.BAD_REQUEST,
                    "CALLER_AND_CALLEE_MUST_DIFFER",
                    "Caller and callee must be different users"
            );
        }
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationException(
                    HttpStatus.NOT_FOUND,
                    "CONVERSATION_NOT_FOUND",
                    "Conversation not found"
            );
        }
        requireMember(conversationId, callerId, "caller");
        requireMember(conversationId, calleeId, "callee");

        // For 1-to-1 calls: reject if conversation already has an active
        // ringing/accepted call. This prevents duplicate invites.
        if (hasActiveCall(conversationId)) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "CALL_ALREADY_ACTIVE",
                    "A call is already active in this conversation"
            );
        }

        String channelName = AgoraChannelNameGenerator.generate(conversationId);
        LocalDateTime now = LocalDateTime.now(clock);

        Call call = Call.ringing(
                conversationId,
                callerId,
                calleeId,
                Call.CallType.VOICE,
                channelName,
                now
        );
        callRepository.save(call);

        // Realtime: notify the callee (and cross-device caller) that an
        // incoming call exists.
        publishCallEvent(call, CallEvent::incoming, /*alsoCaller=*/ true);

        return toResponse(call);
    }

    @Transactional
    public CallResponse acceptCall(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        if (!call.getCalleeId().equals(userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "ONLY_CALLEE_CAN_ACCEPT",
                    "Only the callee can accept the call"
            );
        }
        if (call.getStatus() != Call.Status.RINGING) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "Call is not in RINGING state"
            );
        }

        call.markAccepted(LocalDateTime.now(clock));
        publishCallEvent(call, CallEvent::accepted, /*alsoCaller=*/ true);
        return toResponse(call);
    }

    @Transactional
    public CallResponse rejectCall(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        if (!call.getCalleeId().equals(userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "ONLY_CALLEE_CAN_REJECT",
                    "Only the callee can reject the call"
            );
        }
        if (call.getStatus() != Call.Status.RINGING) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "Call is not in RINGING state"
            );
        }

        call.markRejected(LocalDateTime.now(clock));
        publishCallEvent(call, CallEvent::rejected, /*alsoCaller=*/ true);
        return toResponse(call);
    }

    @Transactional
    public CallResponse cancelCall(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        if (!call.getCallerId().equals(userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "ONLY_CALLER_CAN_CANCEL",
                    "Only the caller can cancel the call"
            );
        }
        if (call.getStatus() != Call.Status.RINGING) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "Call is not in RINGING state"
            );
        }

        call.markCancelled(LocalDateTime.now(clock));
        publishCallEvent(call, CallEvent::cancelled, /*alsoCaller=*/ false);
        return toResponse(call);
    }

    @Transactional
    public CallResponse endCall(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        if (!call.getCallerId().equals(userId) && !call.getCalleeId().equals(userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_PARTICIPANT",
                    "Only call participants can end the call"
            );
        }
        if (call.getStatus() != Call.Status.ACCEPTED) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "Call must be ACCEPTED to be ended"
            );
        }

        Call.EndReason reason = call.getCallerId().equals(userId)
                ? Call.EndReason.ENDED_BY_CALLER
                : Call.EndReason.ENDED_BY_CALLEE;
        call.markEnded(userId, reason, LocalDateTime.now(clock));
        publishCallEvent(call, CallEvent::ended, /*alsoCaller=*/ true);
        return toResponse(call);
    }

    /**
     * Issue a temporary Agora RTC token for the given call. Only the caller
     * or callee may request a token. The token is short-lived and is not
     * persisted.
     */
    @Transactional(readOnly = true)
    public CallTokenResponse issueToken(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        if (!call.getCallerId().equals(userId) && !call.getCalleeId().equals(userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_PARTICIPANT",
                    "Only call participants can request a token"
            );
        }
        if (call.getStatus() != Call.Status.ACCEPTED && call.getStatus() != Call.Status.RINGING) {
            throw new CallException(
                    HttpStatus.CONFLICT,
                    "CALL_NOT_JOINABLE",
                    "Call is not in a state where a token can be issued"
            );
        }

        // The App ID is public. The App Certificate is server-only and is
        // never returned.
        String token = agoraTokenService.generateRtcToken(
                call.getAgoraChannelName(),
                userId,
                Math.toIntExact(agoraProperties.getTokenTtlSeconds())
        );
        return new CallTokenResponse(
                call.getId(),
                call.getAgoraChannelName(),
                token,
                agoraProperties.getAppId(),
                Math.toIntExact(agoraProperties.getTokenTtlSeconds())
        );
    }

    @Transactional(readOnly = true)
    public CallHistoryResponse listHistory(Long userId, int page, int size) {
        Page<Call> calls = callRepository.findByUserIdOrderByStartedAtDesc(
                userId, PageRequest.of(page, size)
        );
        return toHistoryResponse(calls);
    }

    @Transactional(readOnly = true)
    public CallHistoryResponse listConversationHistory(
            Long userId, Long conversationId, int page, int size
    ) {
        requireMember(conversationId, userId, "user");
        Page<Call> calls = callRepository.findByConversationIdOrderByStartedAtDesc(
                conversationId, PageRequest.of(page, size)
        );
        return toHistoryResponse(calls);
    }

    @Transactional(readOnly = true)
    public CallResponse getCall(Long userId, Long callId) {
        Call call = loadCallForUser(callId, userId);
        return toResponse(call);
    }

    // ---------- helpers ----------

    private void requireMember(Long conversationId, Long userId, String role) {
        if (!memberRepository.existsActiveMember(conversationId, userId)) {
            throw new CallException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "User is not an active member of this conversation (" + role + ")"
            );
        }
    }

    private boolean hasActiveCall(Long conversationId) {
        Page<Call> page = callRepository.findByConversationIdOrderByStartedAtDesc(
                conversationId, PageRequest.of(0, 5)
        );
        return page.getContent().stream()
                .anyMatch(c -> {
                    Call.Status s = c.getStatus();
                    return s == Call.Status.RINGING || s == Call.Status.ACCEPTED;
                });
    }

    private Call loadCallForUser(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(
                        HttpStatus.NOT_FOUND,
                        "CALL_NOT_FOUND",
                        "Call not found"
                ));
        // Membership in the conversation acts as authorization for read
        // access to call metadata.
        if (!call.getCallerId().equals(userId) && !call.getCalleeId().equals(userId)) {
            // Non-participants must at least be members of the conversation.
            if (!memberRepository.existsActiveMember(call.getConversationId(), userId)) {
                throw new CallException(
                        HttpStatus.FORBIDDEN,
                        "NOT_A_PARTICIPANT",
                        "You are not a participant in this call"
                );
            }
        }
        return call;
    }

    private void publishCallEvent(Call call, java.util.function.Function<Call, CallEvent> eventBuilder, boolean alsoCaller) {
        // Resolve both participants' usernames for the wire payload.
        Set<Long> ids = Set.of(call.getCallerId(), call.getCalleeId());
        Map<Long, String> usernames = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        CallEvent event = eventBuilder.apply(call);

        // Callee always receives the event (for incoming/accept/reject).
        String calleeUsername = usernames.get(call.getCalleeId());
        if (calleeUsername != null) {
            messagingTemplate.convertAndSendToUser(calleeUsername, "/queue/calls", event);
        }
        // Caller also receives (cross-device sync) when relevant.
        if (alsoCaller) {
            String callerUsername = usernames.get(call.getCallerId());
            if (callerUsername != null) {
                messagingTemplate.convertAndSendToUser(callerUsername, "/queue/calls", event);
            }
        }
    }

    private CallResponse toResponse(Call call) {
        Set<Long> ids = Set.of(call.getCallerId(), call.getCalleeId());
        Map<Long, String> usernames = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return CallResponse.from(
                call,
                usernames.getOrDefault(call.getCallerId(), "?"),
                usernames.getOrDefault(call.getCalleeId(), "?")
        );
    }

    private CallHistoryResponse toHistoryResponse(Page<Call> page) {
        List<Call> calls = page.getContent();
        Set<Long> userIds = calls.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getCallerId(), c.getCalleeId()))
                .collect(Collectors.toSet());
        Map<Long, String> usernames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<CallResponse> responses = calls.stream()
                .map(c -> CallResponse.from(
                        c,
                        usernames.getOrDefault(c.getCallerId(), "?"),
                        usernames.getOrDefault(c.getCalleeId(), "?")
                ))
                .toList();

        return new CallHistoryResponse(
                responses,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
