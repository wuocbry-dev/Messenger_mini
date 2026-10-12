package com.team503.messenger.file.repository;

import com.team503.messenger.file.entity.StoredFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByObjectKey(String objectKey);

    boolean existsByObjectKey(String objectKey);

    /**
     * True when the file is attached to a message in a conversation where the
     * user is still an active member.
     */
    @Query("""
            SELECT CASE WHEN COUNT(ma) > 0 THEN true ELSE false END
            FROM MessageAttachment ma, Message m, ConversationMember cm
            WHERE ma.fileId = :fileId
              AND m.id = ma.messageId
              AND cm.id.conversationId = m.conversationId
              AND cm.id.userId = :userId
              AND cm.leftAt IS NULL
            """)
    boolean isAttachedInConversationOf(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
