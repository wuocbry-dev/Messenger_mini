package com.team503.messenger.message.repository;

import com.team503.messenger.message.entity.MessageAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    @Query("SELECT ma FROM MessageAttachment ma WHERE ma.messageId = :messageId")
    List<MessageAttachment> findByMessageId(@Param("messageId") Long messageId);
}
