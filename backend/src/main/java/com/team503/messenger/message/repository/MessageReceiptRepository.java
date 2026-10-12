package com.team503.messenger.message.repository;

import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.entity.MessageReceiptId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, MessageReceiptId> {

    @Query("SELECT mr FROM MessageReceipt mr WHERE mr.id.messageId = :messageId")
    List<MessageReceipt> findByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT mr FROM MessageReceipt mr WHERE mr.id.messageId = :messageId AND mr.id.userId = :userId")
    MessageReceipt fetchByMessageAndUser(@Param("messageId") Long messageId, @Param("userId") Long userId);
}
