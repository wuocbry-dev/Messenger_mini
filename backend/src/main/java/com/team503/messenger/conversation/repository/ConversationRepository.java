package com.team503.messenger.conversation.repository;

import com.team503.messenger.conversation.entity.Conversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationMember cm ON cm.id.conversationId = c.id
            WHERE cm.id.userId = :userId
            AND cm.leftAt IS NULL
            ORDER BY c.updatedAt DESC
            """)
    java.util.List<Conversation> findActiveByMemberUserId(@Param("userId") Long userId);
}
