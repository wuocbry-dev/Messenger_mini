package com.team503.messenger.notification.config;

import com.team503.messenger.notification.service.EmailService;
import com.team503.messenger.notification.service.EmailTemplateRenderer;
import com.team503.messenger.notification.service.NoopEmailService;
import com.team503.messenger.notification.service.SmtpEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailService emailService(
            org.springframework.boot.autoconfigure.mail.MailProperties smtp,
            MailProperties properties,
            ObjectProvider<JavaMailSender> mailSender,
            EmailTemplateRenderer renderer) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || isBlank(smtp.getUsername()) || isBlank(smtp.getPassword())) {
            log.warn("SMTP credentials are not configured; falling back to NoopEmailService. "
                    + "Emails will be logged instead of sent until MAIL_USERNAME/MAIL_PASSWORD are set.");
            return new NoopEmailService();
        }
        String from = isBlank(properties.getFrom()) ? smtp.getUsername() : properties.getFrom();
        return new SmtpEmailService(sender, renderer, from, properties.getFromName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
