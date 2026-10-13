package com.team503.messenger.conversation.service;

import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.ConversationMemberResponse;
import com.team503.messenger.conversation.dto.ConversationResponse;
import com.team503.messenger.conversation.dto.CreateDirectConversationRequest;
import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import com.team503.messenger.user.service.AvatarUrlResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final Clock clock;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            AvatarUrlResolver avatarUrlResolver,
            Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.avatarUrlResolver = avatarUrlResolver;
        this.clock = clock;
    }

    @Transactional
    public ConversationDetailResponse createOrGetDirectConversation(Long currentUserId, CreateDirectConversationRequest request) {
        Long targetUserId = request.targetUserId();

        if (currentUserId.equals(targetUserId)) {
            throw new ConversationException(HttpStatus.BAD_REQUEST, "SELF_CONVERSATION", "Cannot create a conversation with yourself");
        }

        userRepository.findById(targetUserId)
                .filter(User::isActive)
                .orElseThrow(() -> new ConversationException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "Target user not found or inactive"
                ));

        LocalDateTime now = LocalDateTime.now(clock);
        String directKey = Conversation.generateDirectKey(currentUserId, targetUserId);

        return conversationRepository.findByDirectKey(directKey)
                .map(existing -> {
                    List<ConversationMember> members = memberRepository.findActiveByConversationId(existing.getId());
                    boolean hasCurrentUser = members.stream().anyMatch(m -> m.getUserId().equals(currentUserId));
                    boolean hasTargetUser = members.stream().anyMatch(m -> m.getUserId().equals(targetUserId));

                    if (!hasCurrentUser || !hasTargetUser) {
                        throw new ConversationException(
                                HttpStatus.BAD_REQUEST,
                                "MEMBERSHIP_ERROR",
                                "Conversation exists but members are invalid"
                        );
                    }
                    return buildDetailResponse(existing, members);
                })
                .orElseGet(() -> {
                    Conversation conversation = Conversation.createDirect(
                            currentUserId,
                            targetUserId,
                            currentUserId,
                            directKey,
                            now
                    );
                    Conversation saved = conversationRepository.save(conversation);

                    ConversationMember ownerMember = ConversationMember.joinAsOwner(saved.getId(), currentUserId, now);
                    ConversationMember targetMember = ConversationMember.join(saved.getId(), targetUserId, now);
                    memberRepository.save(ownerMember);
                    memberRepository.save(targetMember);

                    return buildDetailResponse(saved, List.of(ownerMember, targetMember));
                });
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long currentUserId) {
        List<Conversation> conversations = conversationRepository.findActiveByMemberUserId(currentUserId);
        return conversations.stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(Long currentUserId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(
                        HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found"
                ));

        if (!memberRepository.existsActiveMember(conversationId, currentUserId)) {
            throw new ConversationException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversationId);
        return buildDetailResponse(conversation, members);
    }

    private ConversationDetailResponse buildDetailResponse(Conversation conversation, List<ConversationMember> members) {
        List<Long> memberUserIds = members.stream()
                .map(ConversationMember::getUserId)
                .toList();

        Map<Long, User> userMap = userRepository.findAllById(memberUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<ConversationMemberResponse> memberResponses = members.stream()
                .filter(m -> userMap.containsKey(m.getUserId()))
                .map(m -> {
                    User user = userMap.get(m.getUserId());
                    return ConversationMemberResponse.from(m, user.getUsername(), user.getDisplayName(),
                            user.getAvatarObjectKey(), avatarUrlResolver.resolve(user.getAvatarObjectKey()));
                })
                .toList();

        return ConversationDetailResponse.from(conversation, memberResponses);
    }
}
