package com.team503.messenger.message.repository;

import com.team503.messenger.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    Page<Message> findByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId, Pageable pageable);

    boolean existsByCallId(Long callId);
}
