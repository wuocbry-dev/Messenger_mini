package com.team503.messenger.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.message.dto.MessageResponse;
import com.team503.messenger.message.dto.SendAttachmentRequest;
import com.team503.messenger.message.entity.Message;
import com.team503.messenger.message.service.MessageRealtimePublisher;
import com.team503.messenger.message.service.MessageService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageControllerTest {

    private MessageService messageService;
    private MessageRealtimePublisher realtimePublisher;
    private MessageController controller;

    @BeforeEach
    void setUp() {
        messageService = org.mockito.Mockito.mock(MessageService.class);
        realtimePublisher = org.mockito.Mockito.mock(MessageRealtimePublisher.class);
        controller = new MessageController(messageService, realtimePublisher);
    }

    @Test
    void sendAttachmentPublishesSavedMessageToConversationMembers() {
        AuthenticatedUser currentUser = new AuthenticatedUser(1L, "bob");
        SendAttachmentRequest request = new SendAttachmentRequest(10L, 20L, "IMAGE", null);
        MessageResponse response = new MessageResponse(
                30L,
                10L,
                1L,
                "bob",
                "Bob",
                "IMAGE",
                null,
                LocalDateTime.now(),
                List.of(),
                List.of()
        );
        when(messageService.sendAttachmentMessage(1L, 10L, 20L, Message.Type.IMAGE, null))
                .thenReturn(response);

        MessageResponse actual = controller.sendAttachment(currentUser, 10L, request);

        assertThat(actual).isSameAs(response);
        verify(realtimePublisher).publishToConversation(10L, response);
    }
}
