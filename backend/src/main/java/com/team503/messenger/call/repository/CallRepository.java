package com.team503.messenger.call.repository;

import com.team503.messenger.call.entity.Call;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {

    @Query("SELECT c FROM Call c WHERE c.conversationId = :conversationId ORDER BY c.startedAt DESC")
    Page<Call> findByConversationIdOrderByStartedAtDesc(
            @Param("conversationId") Long conversationId,
            Pageable pageable
    );

    @Query("SELECT c FROM Call c WHERE c.callerId = :userId OR c.calleeId = :userId ORDER BY c.startedAt DESC")
    Page<Call> findByUserIdOrderByStartedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM Call c WHERE c.calleeId = :calleeId AND c.status = 'RINGING' AND c.startedAt < :threshold")
    List<Call> findStaleRingingForCallee(
            @Param("calleeId") Long calleeId,
            @Param("threshold") LocalDateTime threshold
    );
}
