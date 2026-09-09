package com.user.management.integration;

import com.user.management.config.AppProperties;
import com.user.management.exception.IntegrationException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends HTML mail with an optional attachment.
 *
 * <p>Disabled by default. Set {@code app.notification.email.enabled=true} together
 * with the {@code spring.mail.*} credentials to switch it on. While disabled the
 * message is logged instead of sent, so the rest of the flow stays testable.</p>
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getNotification().getEmail().isEnabled();
    }

    public void sendHtml(List<String> recipients, String subject, String htmlBody) {
        send(recipients, subject, htmlBody, null, null, null);
    }

    /**
     * @param attachment      file bytes, or {@code null} for a body-only mail
     * @param attachmentName  file name shown to the recipient
     * @param attachmentType  MIME type of the attachment
     */
    public void send(List<String> recipients,
                     String subject,
                     String htmlBody,
                     byte[] attachment,
                     String attachmentName,
                     String attachmentType) {

        List<String> to = clean(recipients);
        if (to.isEmpty()) {
            throw new IntegrationException("No valid email recipient was supplied");
        }

        if (!isEnabled()) {
            log.warn("Email is disabled (app.notification.email.enabled=false). "
                    + "Would have sent \"{}\" to {}{}", subject, to,
                    attachment == null ? "" : " with attachment " + attachmentName);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IntegrationException("Mail is enabled but no mail sender is configured");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null, "UTF-8");
            helper.setFrom(properties.getNotification().getEmail().getFrom());
            helper.setTo(to.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment), attachmentType);
            }
            mailSender.send(message);
            log.info("Email \"{}\" sent to {}", subject, to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            throw new IntegrationException("Could not send the email: " + e.getMessage(), e);
        }
    }

    private List<String> clean(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .filter(r -> r.contains("@") && r.indexOf('@') < r.lastIndexOf('.'))
                .distinct()
                .toList();
    }
}
