package com.team503.messenger.call.repository;

import com.team503.messenger.call.entity.Call;
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

    @Query("SELECT c FROM Call c WHERE c.callerId = :userId OR c.calleeId = :userId ORDER BY c.startedAt DESC")
    Page<Call> findByUserIdOrderByStartedAtDesc(@Param("userId") Long userId, Pageable pageable);

    boolean existsByConversationIdAndStatusIn(Long conversationId, Collection<Call.Status> statuses);

    @Query("SELECT COUNT(c) > 0 FROM Call c WHERE (c.callerId = :userId OR c.calleeId = :userId) AND c.status IN :statuses")
    boolean existsActiveForUser(@Param("userId") Long userId, @Param("statuses") Collection<Call.Status> statuses);

    List<Call> findByStatusAndStartedAtBefore(Call.Status status, LocalDateTime threshold);
}
