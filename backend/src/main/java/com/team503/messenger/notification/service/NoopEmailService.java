package com.team503.messenger.notification.service;

import com.team503.messenger.notification.dto.EmailContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when SMTP is not configured: logs instead of sending so local dev keeps working. */
public class NoopEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailService.class);

    @Override
    public void send(String to, String subject, EmailContent content) {
        log.info("Email not sent (SMTP not configured): to={}, subject={}", to, subject);
    }
}
