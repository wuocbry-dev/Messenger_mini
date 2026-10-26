package com.team503.messenger.notification.service;

import com.team503.messenger.notification.dto.EmailContent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;

public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final String from;
    private final String fromName;

    public SmtpEmailService(JavaMailSender mailSender, EmailTemplateRenderer renderer, String from, String fromName) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.from = from;
        this.fromName = fromName;
    }

    @Async
    @Override
    public void send(String to, String subject, EmailContent content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(renderer.renderText(content), renderer.renderHtml(subject, content));
            mailSender.send(message);
            log.debug("Email sent: to={}, subject={}", to, subject);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            // Runs on an async thread: nobody can catch this, so log it here.
            log.error("Failed to send email: to={}, subject={}", to, subject, ex);
        }
    }
}
