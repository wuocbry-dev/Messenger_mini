package com.team503.messenger.message.service;

import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class MessageRealtimePublisher {

    private static final String MESSAGES_QUEUE = "/queue/messages";

    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageRealtimePublisher(
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void publishToConversation(Long conversationId, MessageResponse message) {
        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversationId);
        Set<Long> memberIds = members.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        Map<Long, String> usernames = userRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        for (ConversationMember member : members) {
            String username = usernames.get(member.getUserId());
            if (username != null) {
                messagingTemplate.convertAndSendToUser(username, MESSAGES_QUEUE, message);
            }
        }
    }
}
