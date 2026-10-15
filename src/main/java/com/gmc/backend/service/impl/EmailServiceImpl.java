package com.gmc.backend.service.impl;

import com.gmc.backend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text email over SMTP. Spring only creates a JavaMailSender when
 * spring.mail.host is set; without it (local development) the message is written to the
 * log instead, so features like password reset can still be tested.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@gmclanka.com}")
    private String from;

    public EmailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Async
    @Override
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Email is not configured (spring.mail.host is not set). Not sent:\n"
                    + "To: {}\nSubject: {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send email '{}' to {}: {}", subject, to, e.getMessage());
        }
    }
}
