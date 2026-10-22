package com.team503.messenger.call.repository;

import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.entity.CallParticipant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {

    Page<Call> findByConversationIdOrderByStartedAtDesc(Long conversationId, Pageable pageable);

    /** Calls the user took part in or was invited to (missed calls included). */
    @Query("""
            SELECT c FROM Call c
            WHERE c.id IN (SELECT p.id.callId FROM CallParticipant p WHERE p.id.userId = :userId)
            ORDER BY c.startedAt DESC
            """)
    Page<Call> findByUserIdOrderByStartedAtDesc(@Param("userId") Long userId, Pageable pageable);

    boolean existsByConversationIdAndStatusIn(Long conversationId, Collection<Call.Status> statuses);

    List<Call> findByStatusAndStartedAtBefore(Call.Status status, LocalDateTime threshold);

    /** Running calls in which the user has the given participant state (e.g. still being rung). */
    @Query("""
            SELECT c FROM Call c, CallParticipant p
            WHERE c.id = p.id.callId
              AND p.id.userId = :userId
              AND p.state = :state
              AND c.status IN :statuses
            ORDER BY c.startedAt DESC
            """)
    List<Call> findForParticipantInState(
            @Param("userId") Long userId,
            @Param("state") CallParticipant.State state,
            @Param("statuses") Collection<Call.Status> statuses
    );

    /** Calls with the given status in conversations the user currently belongs to. */
    @Query("""
            SELECT c FROM Call c
            WHERE c.status = :status
              AND c.conversationId IN (
                  SELECT m.id.conversationId FROM ConversationMember m
                  WHERE m.id.userId = :userId AND m.leftAt IS NULL)
            ORDER BY c.startedAt DESC
            """)
    List<Call> findInConversationsOfUser(@Param("userId") Long userId, @Param("status") Call.Status status);
}
