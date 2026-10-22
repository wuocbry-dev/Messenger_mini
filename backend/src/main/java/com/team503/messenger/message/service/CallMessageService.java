package com.team503.messenger.message.service;

import com.team503.messenger.call.entity.Call;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.message.dto.MessageReceiptResponse;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.repository.MessageReceiptRepository;
import com.team503.messenger.message.repository.MessageRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records finished calls in the chat timeline: one CALL message per call,
 * sent by the caller at the moment the call finished. Members receive it over
 * the same realtime queue as ordinary messages.
 */
@Service
public class CallMessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CallSummaryResolver callSummaryResolver;
    private final MessageRealtimePublisher realtimePublisher;
    private final Clock clock;

    public CallMessageService(
            MessageRepository messageRepository,
            MessageReceiptRepository receiptRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            CallSummaryResolver callSummaryResolver,
            MessageRealtimePublisher realtimePublisher,
            Clock clock
    ) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.callSummaryResolver = callSummaryResolver;
        this.realtimePublisher = realtimePublisher;
        this.clock = clock;
    }

    /**
     * Adds the CALL message for a call that has just finished. Calls still
     * ringing or in progress are ignored, and each call is recorded once.
     */
    @Transactional
    public void recordFinishedCall(Call call) {
        if (!call.getStatus().isTerminal() || messageRepository.existsByCallId(call.getId())) {
            return;
        }
        LocalDateTime at = call.getEndedAt() != null ? call.getEndedAt() : LocalDateTime.now(clock);
        Long conversationId = call.getConversationId();
        Message saved = messageRepository.save(Message.call(conversationId, call.getCallerId(), call.getId(), at));

        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.touch(at);
            conversationRepository.save(conversation);
        });

        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversationId);
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(call.getCallerId())) {
                receiptRepository.save(MessageReceipt.forMessage(saved.getId(), member.getUserId(), at));
            }
        }

        MessageResponse response = toResponse(saved, call);
        afterCommit(() -> realtimePublisher.publishToConversation(conversationId, response));
    }

    private MessageResponse toResponse(Message message, Call call) {
        User caller = userRepository.findById(call.getCallerId()).orElse(null);
        List<MessageReceipt> receipts = receiptRepository.findByMessageId(message.getId());
        Map<Long, User> recipients = userRepository
                .findAllById(receipts.stream().map(MessageReceipt::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MessageReceiptResponse> receiptResponses = receipts.stream()
                .map(r -> {
                    User recipient = recipients.get(r.getUserId());
                    return new MessageReceiptResponse(
                            r.getUserId(),
                            recipient != null ? recipient.getUsername() : "?",
                            r.getDeliveredAt(),
                            r.getReadAt());
                })
                .toList();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                caller != null ? caller.getUsername() : "?",
                caller != null ? caller.getDisplayName() : "?",
                message.getType(),
                message.getContent(),
                message.getCreatedAt(),
                receiptResponses,
                List.of(),
                callSummaryResolver.resolve(List.of(call.getId())).get(call.getId())
        );
    }

    /** Clients must never see a message before it is committed. */
    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
