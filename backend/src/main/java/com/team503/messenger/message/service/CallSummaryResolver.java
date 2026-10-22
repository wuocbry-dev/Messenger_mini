package com.team503.messenger.message.service;

import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.entity.CallParticipant;
import com.team503.messenger.call.repository.CallParticipantRepository;
import com.team503.messenger.call.repository.CallRepository;
import com.team503.messenger.message.dto.MessageCallResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Loads the call details shown on CALL messages, batched per page of messages. */
@Component
public class CallSummaryResolver {

    private final CallRepository callRepository;
    private final CallParticipantRepository participantRepository;

    public CallSummaryResolver(CallRepository callRepository, CallParticipantRepository participantRepository) {
        this.callRepository = callRepository;
        this.participantRepository = participantRepository;
    }

    public Map<Long, MessageCallResponse> resolve(Collection<Long> callIds) {
        if (callIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<CallParticipant>> participants = participantRepository.findByCallIds(callIds).stream()
                .collect(Collectors.groupingBy(CallParticipant::getCallId));
        return callRepository.findAllById(callIds).stream()
                .map(call -> summarize(call, participants.getOrDefault(call.getId(), List.of())))
                .collect(Collectors.toMap(MessageCallResponse::callId, Function.identity()));
    }

    static MessageCallResponse summarize(Call call, List<CallParticipant> participants) {
        Long duration = call.getAnsweredAt() != null && call.getEndedAt() != null
                ? Math.max(0, Duration.between(call.getAnsweredAt(), call.getEndedAt()).getSeconds())
                : null;
        int joined = (int) participants.stream().filter(p -> p.getJoinedAt() != null).count();
        return new MessageCallResponse(
                call.getId(),
                call.getCallType().name(),
                call.getStatus().name(),
                call.getCallerId(),
                call.getStartedAt(),
                call.getAnsweredAt(),
                call.getEndedAt(),
                duration,
                participants.size() > 2,
                joined
        );
    }
}
