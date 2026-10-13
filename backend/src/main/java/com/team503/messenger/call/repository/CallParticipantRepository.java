package com.team503.messenger.call.repository;

import com.team503.messenger.call.entity.Call;
import com.team503.messenger.call.entity.CallParticipant;
import com.team503.messenger.call.entity.CallParticipantId;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CallParticipantRepository extends JpaRepository<CallParticipant, CallParticipantId> {

    @Query("SELECT p FROM CallParticipant p WHERE p.id.callId = :callId ORDER BY p.invitedAt, p.id.userId")
    List<CallParticipant> findByCallId(@Param("callId") Long callId);

    @Query("SELECT p FROM CallParticipant p WHERE p.id.callId IN :callIds")
    List<CallParticipant> findByCallIds(@Param("callIds") Collection<Long> callIds);

    @Query("SELECT p FROM CallParticipant p WHERE p.id.callId = :callId AND p.id.userId = :userId")
    Optional<CallParticipant> findByCallIdAndUserId(@Param("callId") Long callId, @Param("userId") Long userId);

    /** True if the user is ringing or in a call that has not finished. */
    @Query("""
            SELECT COUNT(p) > 0 FROM CallParticipant p, Call c
            WHERE c.id = p.id.callId
              AND p.id.userId = :userId
              AND p.state IN :participantStates
              AND c.status IN :callStatuses
            """)
    boolean existsBusy(
            @Param("userId") Long userId,
            @Param("participantStates") Collection<CallParticipant.State> participantStates,
            @Param("callStatuses") Collection<Call.Status> callStatuses
    );

    /** Invitations in a running call that were never answered. */
    @Query("""
            SELECT p FROM CallParticipant p, Call c
            WHERE c.id = p.id.callId
              AND c.status = :callStatus
              AND p.state = :state
              AND p.invitedAt < :threshold
            """)
    List<CallParticipant> findStaleInvitations(
            @Param("callStatus") Call.Status callStatus,
            @Param("state") CallParticipant.State state,
            @Param("threshold") LocalDateTime threshold
    );
}
