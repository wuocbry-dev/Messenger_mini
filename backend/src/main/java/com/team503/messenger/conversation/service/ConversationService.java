package com.team503.messenger.conversation.service;

import com.team503.messenger.conversation.dto.AddMembersRequest;
import com.team503.messenger.conversation.dto.ConversationDetailResponse;
import com.team503.messenger.conversation.dto.CreateGroupConversationRequest;
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

    /** Upper bound on people in one group conversation. */
    public static final int MAX_GROUP_MEMBERS = 50;

    @Transactional
    public ConversationDetailResponse createGroup(Long creatorId, CreateGroupConversationRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Conversation conversation = createGroupConversation(creatorId, request.title(), request.memberIds(), now);
        return buildDetailResponse(conversation, memberRepository.findActiveByConversationId(conversation.getId()));
    }

    /**
     * Create a group with the creator as owner. Also used when a call is
     * widened to more people, so the group chat appears automatically.
     */
    @Transactional
    public Conversation createGroupConversation(
            Long creatorId,
            String title,
            java.util.Collection<Long> otherUserIds,
            LocalDateTime now
    ) {
        java.util.Set<Long> others = new java.util.LinkedHashSet<>(otherUserIds);
        others.remove(creatorId);
        others.remove(null);
        if (others.size() < 2) {
            throw new ConversationException(
                    HttpStatus.BAD_REQUEST,
                    "GROUP_TOO_SMALL",
                    "A group needs at least 3 people"
            );
        }
        if (others.size() + 1 > MAX_GROUP_MEMBERS) {
            throw new ConversationException(
                    HttpStatus.BAD_REQUEST,
                    "GROUP_TOO_LARGE",
                    "A group can have at most " + MAX_GROUP_MEMBERS + " people"
            );
        }
        requireActiveUsers(others);

        Conversation saved = conversationRepository.save(Conversation.createGroup(title, creatorId, now));
        memberRepository.save(ConversationMember.joinAsOwner(saved.getId(), creatorId, now));
        for (Long userId : others) {
            memberRepository.save(ConversationMember.join(saved.getId(), userId, now));
        }
        return saved;
    }

    @Transactional
    public ConversationDetailResponse addMembers(Long actorId, Long conversationId, AddMembersRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(
                        HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found"
                ));
        if (!memberRepository.existsActiveMember(conversationId, actorId)) {
            throw new ConversationException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }
        addMembersToGroup(conversation, request.userIds(), LocalDateTime.now(clock));
        return buildDetailResponse(conversation, memberRepository.findActiveByConversationId(conversationId));
    }

    /** Adds (or re-activates) members of a group; returns the ids that were newly added. */
    @Transactional
    public List<Long> addMembersToGroup(Conversation conversation, java.util.Collection<Long> userIds, LocalDateTime now) {
        if (!conversation.isGroup()) {
            throw new ConversationException(
                    HttpStatus.BAD_REQUEST,
                    "NOT_A_GROUP",
                    "People can only be added to group conversations"
            );
        }
        java.util.Set<Long> wanted = new java.util.LinkedHashSet<>(userIds);
        wanted.remove(null);
        requireActiveUsers(wanted);

        List<ConversationMember> current = memberRepository.findActiveByConversationId(conversation.getId());
        java.util.Set<Long> already = current.stream().map(ConversationMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> toAdd = wanted.stream().filter(id -> !already.contains(id)).toList();
        if (current.size() + toAdd.size() > MAX_GROUP_MEMBERS) {
            throw new ConversationException(
                    HttpStatus.BAD_REQUEST,
                    "GROUP_TOO_LARGE",
                    "A group can have at most " + MAX_GROUP_MEMBERS + " people"
            );
        }
        for (Long userId : toAdd) {
            ConversationMember existing = memberRepository
                    .findByConversationIdAndUserId(conversation.getId(), userId).orElse(null);
            if (existing != null) {
                existing.rejoin(now);
            } else {
                memberRepository.save(ConversationMember.join(conversation.getId(), userId, now));
            }
        }
        conversation.touch(now);
        return toAdd;
    }

    private void requireActiveUsers(java.util.Collection<Long> ids) {
        long active = userRepository.findAllById(ids).stream().filter(User::isActive).count();
        if (active != ids.size()) {
            throw new ConversationException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "One or more users were not found or are inactive"
            );
        }
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
