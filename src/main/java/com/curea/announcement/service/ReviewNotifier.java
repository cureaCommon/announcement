package com.curea.announcement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ReviewNotifier {

    private static final Logger logger = LoggerFactory.getLogger(ReviewNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String[] recipients;
    private final String from;
    private final String frontendBaseUrl;

    public ReviewNotifier(ObjectProvider<JavaMailSender> mailSender,
                          @Value("${announcement.admin-emails:}") String adminEmails,
                          @Value("${announcement.mail-from:}") String from,
                          @Value("${frontend.base.url:}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.recipients = adminEmails == null ? new String[0]
                : Arrays.stream(adminEmails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
    }

    public void awaitingReview(Long announcementId, String title, boolean revision) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || recipients.length == 0) {
            logger.info("Announcement {} is awaiting review; no mail sent (mail or recipients not configured)", announcementId);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(recipients);
            message.setSubject(revision ? "Onay bekleyen duyuru değişikliği: " + title : "Onay bekleyen duyuru: " + title);
            message.setText((revision ? "Yayındaki bir duyuruda yapılan değişiklik onayınızı bekliyor." : "Yeni bir duyuru onayınızı bekliyor.")
                    + "\n\nBaşlık: " + title
                    + "\nİncelemek için: " + frontendBaseUrl + "/announcements/manage?tab=review");
            sender.send(message);
        } catch (RuntimeException ex) {
            logger.warn("Review notification mail could not be sent for announcement {}", announcementId, ex);
        }
    }
}
