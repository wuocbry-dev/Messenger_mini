package com.team503.messenger.call.service;

import com.team503.messenger.call.config.AgoraProperties;
import com.team503.messenger.call.dto.CallEvent;
import com.team503.messenger.call.dto.CallEvent.EventType;
import com.team503.messenger.call.dto.CallHistoryResponse;
import com.team503.messenger.call.dto.CallParticipantResponse;
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
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.conversation.service.ConversationService;
import com.team503.messenger.message.service.CallMessageService;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import com.team503.messenger.user.service.AvatarUrlResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Voice/video call business logic for 1-to-1 and group calls. Agora carries
 * the media; this service owns authorization, call state, participants,
 * channel naming, token issuance and history.
 *
 * <p>A call has one row in {@code calls} and one row per person in
 * {@code call_participants}. The call is RINGING until an invitee answers,
 * ACCEPTED while people are talking, and ends when one or nobody is left.
 */
@Service
public class CallService {

    /** Most people in one call (the caller included). */
    public static final int MAX_PARTICIPANTS = 8;

    private static final Set<Call.Status> ACTIVE = EnumSet.of(Call.Status.RINGING, Call.Status.ACCEPTED);
    private static final Set<State> PRESENT = EnumSet.of(State.INVITED, State.JOINED);

    private final CallRepository callRepository;
    private final CallParticipantRepository participantRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AgoraTokenService agoraTokenService;
    private final AgoraProperties agoraProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final CallMessageService callMessageService;
    private final Clock clock;

    public CallService(
            CallRepository callRepository,
            CallParticipantRepository participantRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            ConversationService conversationService,
            UserRepository userRepository,
            AvatarUrlResolver avatarUrlResolver,
            AgoraTokenService agoraTokenService,
            AgoraProperties agoraProperties,
            SimpMessagingTemplate messagingTemplate,
            CallMessageService callMessageService,
            Clock clock
    ) {
        this.callRepository = callRepository;
        this.participantRepository = participantRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.avatarUrlResolver = avatarUrlResolver;
        this.agoraTokenService = agoraTokenService;
        this.agoraProperties = agoraProperties;
        this.messagingTemplate = messagingTemplate;
        this.callMessageService = callMessageService;
        this.clock = clock;
    }

    // ---------- starting ----------

    /**
     * Starts a call in a conversation. In a 1-to-1 conversation the other
     * person is rung; in a group every other member is rung (up to the limit).
     * {@code calleeId} may be given for a 1-to-1 call and must then match.
     */
    @Transactional
    public CallResponse startCall(Long callerId, Long conversationId, Long calleeId, Call.CallType callType) {
        if (callerId.equals(calleeId)) {
            throw new CallException(HttpStatus.BAD_REQUEST, "CALLER_AND_CALLEE_MUST_DIFFER",
                    "Caller and callee must be different users");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "Conversation not found"));
        requireMember(conversationId, callerId, "caller");

        List<Long> invitees = resolveInvitees(conversation, callerId, calleeId);
        if (invitees.size() + 1 > MAX_PARTICIPANTS) {
            throw new CallException(HttpStatus.BAD_REQUEST, "CALL_TOO_LARGE",
                    "A call supports at most " + MAX_PARTICIPANTS + " people");
        }

        if (callRepository.existsByConversationIdAndStatusIn(conversationId, ACTIVE)) {
            throw new CallException(HttpStatus.CONFLICT, "CALL_ALREADY_ACTIVE",
                    "A call is already active in this conversation");
        }
        if (isBusy(callerId)) {
            throw new CallException(HttpStatus.CONFLICT, "CALLER_BUSY", "You are already in a call");
        }
        // People already in another call are skipped; if nobody is left, fail.
        List<Long> reachable = invitees.stream().filter(id -> !isBusy(id)).toList();
        if (reachable.isEmpty()) {
            throw new CallException(HttpStatus.CONFLICT, "CALLEE_BUSY",
                    invitees.size() == 1 ? "The other user is already in a call" : "Everyone is already in a call");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Long singleCallee = !conversation.isGroup() ? reachable.get(0) : null;
        Call call = callRepository.save(Call.ringing(
                conversationId,
                callerId,
                singleCallee,
                callType,
                AgoraChannelNameGenerator.generate(conversationId),
                now
        ));
        participantRepository.save(CallParticipant.caller(call.getId(), callerId, now));
        for (Long id : reachable) {
            participantRepository.save(CallParticipant.invited(call.getId(), id, callerId, now));
        }

        publish(EventType.INCOMING, call, callerId, reachable);
        return toResponse(call);
    }

    // ---------- answering ----------

    @Transactional
    public CallResponse acceptCall(Long userId, Long callId) {
        Call call = loadActive(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (!me.isIn(State.INVITED)) {
            throw new CallException(HttpStatus.FORBIDDEN, "ONLY_INVITEE_CAN_ACCEPT",
                    "Only someone being rung can accept the call");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        me.join(now);
        boolean first = call.getStatus() == Call.Status.RINGING;
        if (first) {
            call.markAccepted(now);
        }
        publish(first ? EventType.ACCEPTED : EventType.PARTICIPANT_JOINED, call, userId, everyone(callId));
        return toResponse(call);
    }

    @Transactional
    public CallResponse rejectCall(Long userId, Long callId) {
        Call call = loadActive(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (!me.isIn(State.INVITED)) {
            throw new CallException(HttpStatus.FORBIDDEN, "ONLY_INVITEE_CAN_REJECT",
                    "Only someone being rung can reject the call");
        }
        me.decline(LocalDateTime.now(clock));

        boolean nobodyElseRinging = participantRepository.findByCallId(callId).stream()
                .noneMatch(p -> p.isIn(State.INVITED));
        if (call.getStatus() == Call.Status.RINGING && nobodyElseRinging) {
            call.markRejected(userId, LocalDateTime.now(clock));
            callMessageService.recordFinishedCall(call);
            publish(EventType.REJECTED, call, userId, everyone(callId));
        } else {
            publish(EventType.PARTICIPANT_DECLINED, call, userId, everyone(callId));
        }
        return toResponse(call);
    }

    /** The caller hangs up while nobody has answered yet. */
    @Transactional
    public CallResponse cancelCall(Long userId, Long callId) {
        Call call = loadActive(callId);
        participantOrForbidden(callId, userId);
        if (!call.getCallerId().equals(userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, "ONLY_CALLER_CAN_CANCEL",
                    "Only the caller can cancel the call");
        }
        requireRinging(call);
        LocalDateTime now = LocalDateTime.now(clock);
        call.markCancelled(now);
        for (CallParticipant p : participantRepository.findByCallId(callId)) {
            if (p.isIn(State.INVITED)) {
                p.miss(now);
            } else if (p.isIn(State.JOINED)) {
                p.leave(now);
            }
        }
        callMessageService.recordFinishedCall(call);
        publish(EventType.CANCELLED, call, userId, everyone(callId));
        return toResponse(call);
    }

    // ---------- during the call ----------

    /**
     * Leaves a call that is in progress. When fewer than two people remain the
     * whole call ends; otherwise it carries on without this person.
     */
    @Transactional
    public CallResponse endCall(Long userId, Long callId) {
        Call call = loadActive(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (call.getStatus() != Call.Status.ACCEPTED) {
            throw new CallException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                    "Call must be ACCEPTED to be ended");
        }
        if (!me.isIn(State.JOINED)) {
            throw new CallException(HttpStatus.CONFLICT, "NOT_IN_CALL", "You are not in this call");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        me.leave(now);

        List<CallParticipant> all = participantRepository.findByCallId(callId);
        long stillIn = all.stream().filter(p -> p.isIn(State.JOINED)).count();
        if (stillIn <= 1) {
            for (CallParticipant p : all) {
                if (p.isIn(State.JOINED)) {
                    p.leave(now);
                } else if (p.isIn(State.INVITED)) {
                    p.miss(now);
                }
            }
            call.markEnded(userId, now);
            callMessageService.recordFinishedCall(call);
            publish(EventType.ENDED, call, userId, everyone(callId));
        } else {
            publish(EventType.PARTICIPANT_LEFT, call, userId, everyone(callId));
        }
        return toResponse(call);
    }

    /**
     * Rings more people into a call. A 1-to-1 conversation cannot grow, so a
     * new group chat with everyone is created and the call moves to it; in a
     * group the invited people are added to that group.
     */
    @Transactional
    public CallResponse invite(Long userId, Long callId, Collection<Long> userIds) {
        Call call = loadActive(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (!me.isIn(State.JOINED)) {
            throw new CallException(HttpStatus.FORBIDDEN, "NOT_IN_CALL", "Join the call before inviting people");
        }

        Set<Long> targets = new LinkedHashSet<>(userIds);
        targets.remove(null);
        targets.remove(userId);
        if (targets.isEmpty()) {
            throw new CallException(HttpStatus.BAD_REQUEST, "NO_ONE_TO_INVITE", "Choose at least one person");
        }
        if (userRepository.findAllById(targets).stream().filter(User::isActive).count() != targets.size()) {
            throw new CallException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                    "One or more users were not found or are inactive");
        }

        List<CallParticipant> current = participantRepository.findByCallId(callId);
        Map<Long, CallParticipant> byUser = current.stream()
                .collect(Collectors.toMap(CallParticipant::getUserId, Function.identity()));
        // Skip people already ringing or in the call.
        targets.removeIf(id -> byUser.get(id) != null && byUser.get(id).isIn(State.INVITED, State.JOINED));
        if (targets.isEmpty()) {
            return toResponse(call);
        }
        long present = current.stream().filter(p -> p.isIn(State.INVITED, State.JOINED)).count();
        if (present + targets.size() > MAX_PARTICIPANTS) {
            throw new CallException(HttpStatus.BAD_REQUEST, "CALL_TOO_LARGE",
                    "A call supports at most " + MAX_PARTICIPANTS + " people");
        }
        List<Long> busy = targets.stream().filter(this::isBusy).toList();
        if (!busy.isEmpty()) {
            throw new CallException(HttpStatus.CONFLICT, "CALLEE_BUSY",
                    busy.size() == 1 ? "That person is already in a call" : "Some people are already in a call");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean conversationChanged = moveToGroupConversation(call, userId, current, targets, now);

        for (Long id : targets) {
            CallParticipant existing = byUser.get(id);
            if (existing != null) {
                existing.reinvite(userId, now);
            } else {
                participantRepository.save(CallParticipant.invited(callId, id, userId, now));
            }
        }

        List<Long> newInvitees = new ArrayList<>(targets);
        // Ring the new people; tell everyone else the roster changed.
        publish(EventType.INCOMING, call, userId, newInvitees);
        List<Long> others = everyone(callId).stream().filter(id -> !newInvitees.contains(id)).toList();
        publish(conversationChanged ? EventType.CONVERSATION_CHANGED : EventType.PARTICIPANT_INVITED,
                call, userId, others);
        return toResponse(call);
    }

    /** Switches a voice call to video for everyone (each person still opts in with their camera). */
    @Transactional
    public CallResponse upgradeToVideo(Long userId, Long callId) {
        Call call = loadActive(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (!me.isIn(State.JOINED)) {
            throw new CallException(HttpStatus.FORBIDDEN, "NOT_IN_CALL", "You are not in this call");
        }
        if (call.getCallType() != Call.CallType.VIDEO) {
            call.upgradeToVideo();
            publish(EventType.MEDIA_UPGRADED, call, userId, everyone(callId));
        }
        return toResponse(call);
    }

    /**
     * Issues a temporary RTC token to someone who is in the call. People being
     * rung get one only after they answer. The uid is the user's numeric id and
     * the token is bound to it. Tokens are not stored.
     */
    @Transactional(readOnly = true)
    public CallTokenResponse issueToken(Long userId, Long callId) {
        Call call = callOrNotFound(callId);
        CallParticipant me = participantOrForbidden(callId, userId);
        if (!ACTIVE.contains(call.getStatus()) || !me.isIn(State.JOINED)) {
            throw new CallException(HttpStatus.CONFLICT, "CALL_NOT_JOINABLE",
                    "Call is not in a state where a token can be issued");
        }
        int ttl = Math.toIntExact(agoraProperties.getTokenTtlSeconds());
        String token = agoraTokenService.generateRtcToken(call.getAgoraChannelName(), userId, ttl);
        return new CallTokenResponse(call.getId(), call.getAgoraChannelName(), token,
                agoraProperties.getAppId(), userId, ttl);
    }

    // ---------- timeouts ----------

    /**
     * Calls nobody answered in time become MISSED; people added to a running
     * call who never answered are marked missed too.
     */
    @Transactional
    public int expireStaleRinging() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minusSeconds(agoraProperties.getRingTimeoutSeconds());

        List<Call> stale = callRepository.findByStatusAndStartedAtBefore(Call.Status.RINGING, threshold);
        for (Call call : stale) {
            for (CallParticipant p : participantRepository.findByCallId(call.getId())) {
                if (p.isIn(State.INVITED)) {
                    p.miss(now);
                } else if (p.isIn(State.JOINED)) {
                    p.leave(now);
                }
            }
            call.markMissed(now);
            callMessageService.recordFinishedCall(call);
            publish(EventType.MISSED, call, null, everyone(call.getId()));
        }

        int late = 0;
        for (CallParticipant p : participantRepository.findStaleInvitations(
                Call.Status.ACCEPTED, State.INVITED, threshold)) {
            Call call = callRepository.findById(p.getCallId()).orElse(null);
            if (call == null) {
                continue;
            }
            p.miss(now);
            publish(EventType.PARTICIPANT_DECLINED, call, p.getUserId(), everyone(call.getId()));
            late++;
        }
        return stale.size() + late;
    }

    // ---------- history ----------

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
        Call call = callOrNotFound(callId);
        participantOrForbidden(callId, userId);
        return toResponse(call);
    }

    // ---------- helpers ----------

    /** Who gets rung: the other person (1-to-1) or all other members (group). */
    private List<Long> resolveInvitees(Conversation conversation, Long callerId, Long calleeId) {
        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversation.getId());
        List<Long> others = members.stream()
                .map(ConversationMember::getUserId)
                .filter(id -> !id.equals(callerId))
                .toList();
        if (calleeId != null && !conversation.isGroup()) {
            if (!others.contains(calleeId)) {
                throw new CallException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
                        "User is not an active member of this conversation (callee)");
            }
            return List.of(calleeId);
        }
        if (others.isEmpty()) {
            throw new CallException(HttpStatus.BAD_REQUEST, "NO_ONE_TO_CALL", "There is nobody to call");
        }
        return others;
    }

    /**
     * Makes sure the call's conversation can hold every participant. Returns
     * true if the call was moved to a newly created group conversation.
     */
    private boolean moveToGroupConversation(
            Call call,
            Long actorId,
            List<CallParticipant> current,
            Collection<Long> newPeople,
            LocalDateTime now
    ) {
        Conversation conversation = conversationRepository.findById(call.getConversationId())
                .orElseThrow(() -> new ConversationException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "Conversation not found"));
        if (conversation.isGroup()) {
            requireMember(conversation.getId(), actorId, "inviter");
            conversationService.addMembersToGroup(conversation, newPeople, now);
            return false;
        }
        Set<Long> everyoneInCall = current.stream()
                .filter(p -> p.isIn(State.INVITED, State.JOINED))
                .map(CallParticipant::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        everyoneInCall.addAll(newPeople);
        Conversation group = conversationService.createGroupConversation(actorId, null, everyoneInCall, now);
        call.changeConversation(group.getId());
        return true;
    }

    private boolean isBusy(Long userId) {
        return participantRepository.existsBusy(userId, PRESENT, ACTIVE);
    }

    private void requireMember(Long conversationId, Long userId, String role) {
        if (!memberRepository.existsActiveMember(conversationId, userId)) {
            throw new CallException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER",
                    "User is not an active member of this conversation (" + role + ")");
        }
    }

    private void requireRinging(Call call) {
        if (call.getStatus() != Call.Status.RINGING) {
            throw new CallException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                    "Call is not in RINGING state");
        }
    }

    private Call callOrNotFound(Long callId) {
        return callRepository.findById(callId)
                .orElseThrow(() -> new CallException(HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", "Call not found"));
    }

    /** The call, which must still be running. */
    private Call loadActive(Long callId) {
        Call call = callOrNotFound(callId);
        if (!ACTIVE.contains(call.getStatus())) {
            throw new CallException(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "The call has already ended");
        }
        return call;
    }

    /** Only people who were part of the call may see or act on it. */
    private CallParticipant participantOrForbidden(Long callId, Long userId) {
        return participantRepository.findByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new CallException(HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT",
                        "You are not a participant in this call"));
    }

    private List<Long> everyone(Long callId) {
        return participantRepository.findByCallId(callId).stream().map(CallParticipant::getUserId).toList();
    }

    /**
     * Builds the event now (inside the transaction) and sends it after commit,
     * so clients never see uncommitted state.
     */
    private void publish(EventType type, Call call, Long actorId, Collection<Long> recipientIds) {
        if (recipientIds.isEmpty()) {
            return;
        }
        CallEvent event = CallEvent.of(type, call, actorId, participantResponses(call.getId()));
        List<String> usernames = userRepository.findAllById(recipientIds).stream()
                .map(User::getUsername)
                .toList();
        Runnable send = () -> usernames.forEach(username ->
                messagingTemplate.convertAndSendToUser(username, "/queue/calls", event));
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

    private List<CallParticipantResponse> participantResponses(Long callId) {
        List<CallParticipant> participants = participantRepository.findByCallId(callId);
        Map<Long, User> users = loadUsers(participants.stream().map(CallParticipant::getUserId).toList());
        return participants.stream().map(p -> participantResponse(p, users.get(p.getUserId()))).toList();
    }

    private CallParticipantResponse participantResponse(CallParticipant p, User user) {
        return CallParticipantResponse.of(p, user,
                user == null ? null : avatarUrlResolver.resolve(user.getAvatarObjectKey()));
    }

    private Map<Long, User> loadUsers(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private CallResponse toResponse(Call call) {
        return toResponses(List.of(call)).get(0);
    }

    private CallHistoryResponse toHistoryResponse(Page<Call> page) {
        return new CallHistoryResponse(toResponses(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    private List<CallResponse> toResponses(List<Call> calls) {
        if (calls.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CallParticipant>> byCall = participantRepository
                .findByCallIds(calls.stream().map(Call::getId).toList()).stream()
                .collect(Collectors.groupingBy(CallParticipant::getCallId));

        Set<Long> userIds = new LinkedHashSet<>();
        for (Call c : calls) {
            userIds.add(c.getCallerId());
            if (c.getCalleeId() != null) {
                userIds.add(c.getCalleeId());
            }
        }
        byCall.values().forEach(list -> list.forEach(p -> userIds.add(p.getUserId())));
        Map<Long, User> users = loadUsers(userIds);

        return calls.stream().map(c -> {
            List<CallParticipantResponse> participants = byCall.getOrDefault(c.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(CallParticipant::getInvitedAt)
                            .thenComparing(CallParticipant::getUserId))
                    .map(p -> participantResponse(p, users.get(p.getUserId())))
                    .toList();
            User caller = users.get(c.getCallerId());
            User callee = c.getCalleeId() == null ? null : users.get(c.getCalleeId());
            return CallResponse.from(c,
                    caller != null ? caller.getUsername() : "?",
                    callee != null ? callee.getUsername() : null,
                    participants);
        }).toList();
    }
}
