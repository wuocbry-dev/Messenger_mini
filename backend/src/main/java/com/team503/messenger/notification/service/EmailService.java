package com.team503.messenger.notification.service;

import com.team503.messenger.notification.dto.EmailContent;

public interface EmailService {

    /** Sends an HTML email (with plain-text fallback) built from the shared layout. Runs asynchronously. */
    void send(String to, String subject, EmailContent content);
}
