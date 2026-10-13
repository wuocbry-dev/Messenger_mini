package com.team503.messenger.message.service;

import com.team503.messenger.conversation.entity.Conversation;
import com.team503.messenger.conversation.entity.ConversationMember;
import com.team503.messenger.conversation.exception.ConversationException;
import com.team503.messenger.conversation.repository.ConversationMemberRepository;
import com.team503.messenger.conversation.repository.ConversationRepository;
import com.team503.messenger.file.entity.StoredFile;
import com.team503.messenger.file.exception.FileException;
import com.team503.messenger.file.repository.StoredFileRepository;
import com.team503.messenger.message.dto.MessageAttachmentResponse;
import com.team503.messenger.message.dto.MessagePageResponse;
import com.team503.messenger.message.dto.MessageReceiptResponse;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendMessageRequest;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.entity.MessageAttachment;
import com.team503.messenger.message.entity.MessageReceipt;
import com.team503.messenger.message.exception.MessageException;
import com.team503.messenger.message.repository.MessageAttachmentRepository;
import com.team503.messenger.message.repository.MessageReceiptRepository;
import com.team503.messenger.message.repository.MessageRepository;
import com.team503.messenger.user.entity.User;
import com.team503.messenger.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final Clock clock;

    public MessageService(
            MessageRepository messageRepository,
            MessageReceiptRepository receiptRepository,
            MessageAttachmentRepository attachmentRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository,
            StoredFileRepository storedFileRepository,
            Clock clock
    ) {
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.attachmentRepository = attachmentRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.storedFileRepository = storedFileRepository;
        this.clock = clock;
    }

    @Transactional
    public MessageResponse sendMessage(Long senderId, Long conversationId, SendMessageRequest request) {
        return persistAndBuild(senderId, conversationId, Message.Type.TEXT, request.content(), null);
    }

    /**
     * Variant used by both the REST controller and the STOMP controller. Server
     * controls the senderId (taken from the authenticated principal), id, and
     * createdAt. The content is trimmed and validated.
     *
     * @return the persisted {@link MessageResponse} ready to publish over the
     *         messaging broker.
     */
    @Transactional
    public MessageResponse sendChatMessage(Long senderId, Long conversationId, Message.Type type, String content) {
        return persistAndBuild(senderId, conversationId, type, content, null);
    }

    /**
     * Send an IMAGE or FILE message that references an existing
     * {@link StoredFile} (created via the file prepareUpload flow).
     */
    @Transactional
    public MessageResponse sendAttachmentMessage(
            Long senderId,
            Long conversationId,
            Long fileId,
            Message.Type type,
            String content
    ) {
        if (type != Message.Type.IMAGE && type != Message.Type.FILE) {
            throw new MessageException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TYPE_FOR_ATTACHMENT",
                    "Attachment messages must be of type IMAGE or FILE"
            );
        }
        // Verify the file exists and the uploader is the sender; otherwise
        // anyone could attach someone else's upload to their own message.
        StoredFile file = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new MessageException(
                        HttpStatus.NOT_FOUND,
                        "FILE_NOT_FOUND",
                        "File not found"
                ));
        if (!file.getUploaderId().equals(senderId)) {
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "NOT_FILE_OWNER",
                    "You can only attach files you uploaded"
            );
        }
        return persistAndBuild(senderId, conversationId, type, content, fileId);
    }

    private MessageResponse persistAndBuild(
            Long senderId,
            Long conversationId,
            Message.Type type,
            String content,
            Long attachmentFileId
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(
                        HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found"
                ));

        if (!memberRepository.existsActiveMember(conversationId, senderId)) {
            throw new ConversationException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        if ((content == null || content.isBlank()) && attachmentFileId == null) {
            throw new MessageException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_MESSAGE",
                    "Message content cannot be empty"
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);

        Message message = Message.of(conversationId, senderId, type, content == null ? null : content.trim(), now);
        Message saved = messageRepository.save(message);

        // Update conversation updatedAt
        conversation.touch(now);
        conversationRepository.save(conversation);

        // Create receipts for all other active members
        List<ConversationMember> members = memberRepository.findActiveByConversationId(conversationId);
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderId)) {
                MessageReceipt receipt = MessageReceipt.forMessage(saved.getId(), member.getUserId(), now);
                receiptRepository.save(receipt);
            }
        }

        // Optional attachment row
        if (attachmentFileId != null) {
            StoredFile file = storedFileRepository.findById(attachmentFileId)
                    .orElseThrow(() -> new FileException(
                            HttpStatus.NOT_FOUND,
                            "FILE_NOT_FOUND",
                            "File not found"
                    ));
            MessageAttachment attachment = type == Message.Type.IMAGE
                    ? MessageAttachment.image(saved.getId(), file.getId())
                    : MessageAttachment.file(saved.getId(), file.getId());
            attachmentRepository.save(attachment);
        }

        User sender = userRepository.findById(senderId).orElseThrow();
        List<MessageReceipt> receipts = receiptRepository.findByMessageId(saved.getId());
        List<MessageAttachment> attachments = attachmentRepository.findByMessageId(saved.getId());
        return buildMessageResponse(saved, sender, receipts, attachments);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse listMessages(Long currentUserId, Long conversationId, int page, int size) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationException(
                    HttpStatus.NOT_FOUND,
                    "CONVERSATION_NOT_FOUND",
                    "Conversation not found"
            );
        }

        if (!memberRepository.existsActiveMember(conversationId, currentUserId)) {
            throw new ConversationException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        Page<Message> messagePage = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(page, size)
        );

        List<Long> senderIds = messagePage.getContent().stream()
                .map(Message::getSenderId)
                .distinct()
                .toList();
        Map<Long, User> senderMap = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Long> messageIds = messagePage.getContent().stream()
                .map(Message::getId)
                .toList();
        Map<Long, List<MessageAttachment>> attachmentsByMessageId = messageIds.isEmpty()
                ? Map.of()
                : attachmentRepository
                        .findByMessageIdIn(messageIds)
                        .stream()
                        .collect(Collectors.groupingBy(MessageAttachment::getMessageId));
        List<Long> fileIds = attachmentsByMessageId.values().stream()
                .flatMap(List::stream)
                .map(MessageAttachment::getFileId)
                .distinct()
                .toList();
        Map<Long, StoredFile> fileMap = storedFileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, f -> f));

        List<MessageResponse> responses = messagePage.getContent().stream()
                .map(m -> {
                    User sender = senderMap.get(m.getSenderId());
                    String username = sender != null ? sender.getUsername() : "?";
                    String displayName = sender != null ? sender.getDisplayName() : "?";
                    List<MessageAttachmentResponse> attachmentResponses = attachmentsByMessageId
                            .getOrDefault(m.getId(), List.of())
                            .stream()
                            .map(ma -> {
                                StoredFile file = fileMap.get(ma.getFileId());
                                String mimeType = file != null ? file.getMimeType() : null;
                                String originalName = file != null ? file.getOriginalName() : null;
                                Long sizeBytes = file != null ? file.getSizeBytes() : null;
                                return MessageAttachmentResponse.from(ma, mimeType, originalName, sizeBytes);
                            })
                            .toList();
                    return new MessageResponse(
                            m.getId(),
                            m.getConversationId(),
                            m.getSenderId(),
                            username,
                            displayName,
                            m.getType(),
                            m.getContent(),
                            m.getCreatedAt(),
                            List.of(),
                            attachmentResponses
                    );
                })
                .toList();

        return new MessagePageResponse(
                responses,
                messagePage.getNumber(),
                messagePage.getSize(),
                messagePage.getTotalElements(),
                messagePage.getTotalPages(),
                messagePage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessage(Long currentUserId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageException(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_NOT_FOUND",
                        "Message not found"
                ));

        if (!memberRepository.existsActiveMember(message.getConversationId(), currentUserId)) {
            throw new MessageException(
                    HttpStatus.FORBIDDEN,
                    "NOT_A_MEMBER",
                    "You are not a member of this conversation"
            );
        }

        User sender = userRepository.findById(message.getSenderId()).orElseThrow();
        List<MessageReceipt> receipts = receiptRepository.findByMessageId(messageId);
        List<MessageAttachment> attachments = attachmentRepository.findByMessageId(messageId);
        return buildMessageResponse(message, sender, receipts, attachments);
    }

    private MessageResponse buildMessageResponse(
            Message message,
            User sender,
            List<MessageReceipt> receipts,
            List<MessageAttachment> attachments
    ) {
        List<MessageReceiptResponse> receiptResponses = receipts.stream()
                .map(r -> {
                    User recipient = userRepository.findById(r.getUserId()).orElse(null);
                    String username = recipient != null ? recipient.getUsername() : "?";
                    return new MessageReceiptResponse(
                            r.getUserId(),
                            username,
                            r.getDeliveredAt(),
                            r.getReadAt()
                    );
                })
                .toList();

        // Resolve attachment file metadata in one query to avoid N+1.
        List<Long> fileIds = attachments.stream().map(MessageAttachment::getFileId).toList();
        Map<Long, StoredFile> files = storedFileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, f -> f));

        List<MessageAttachmentResponse> attachmentResponses = attachments.stream()
                .map(ma -> {
                    StoredFile f = files.get(ma.getFileId());
                    String mimeType = f != null ? f.getMimeType() : null;
                    String originalName = f != null ? f.getOriginalName() : null;
                    Long sizeBytes = f != null ? f.getSizeBytes() : null;
                    return MessageAttachmentResponse.from(ma, mimeType, originalName, sizeBytes);
                })
                .toList();

        MessageResponse response = MessageResponse.from(message, sender.getUsername(), sender.getDisplayName(), receiptResponses);
        return new MessageResponse(
                response.id(),
                response.conversationId(),
                response.senderId(),
                response.senderUsername(),
                response.senderDisplayName(),
                response.type(),
                response.content(),
                response.createdAt(),
                response.receipts(),
                attachmentResponses
        );
    }
}
