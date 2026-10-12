package com.team503.messenger.message.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_attachments")
public class MessageAttachment {

    public enum AttachmentType {
        IMAGE,
        FILE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "message_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long messageId;

    @Column(name = "file_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long fileId;

    @Column(name = "attachment_type", nullable = false, length = 20)
    private String attachmentType;

    protected MessageAttachment() {
    }

    private MessageAttachment(Long messageId, Long fileId, String attachmentType) {
        this.messageId = messageId;
        this.fileId = fileId;
        this.attachmentType = attachmentType;
    }

    public static MessageAttachment image(Long messageId, Long fileId) {
        return new MessageAttachment(messageId, fileId, AttachmentType.IMAGE.name());
    }

    public static MessageAttachment file(Long messageId, Long fileId) {
        return new MessageAttachment(messageId, fileId, AttachmentType.FILE.name());
    }

    public Long getId() {
        return id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getAttachmentType() {
        return attachmentType;
    }
}
