package com.team503.messenger.conversation.repository;

import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.entity.ConversationMemberId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    @Query("SELECT cm FROM ConversationMember cm WHERE cm.id.conversationId = :conversationId AND cm.leftAt IS NULL")
    List<ConversationMember> findActiveByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END FROM ConversationMember cm WHERE cm.id.conversationId = :conversationId AND cm.id.userId = :userId AND cm.leftAt IS NULL")
    boolean existsActiveMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
}
