package edu.artemiy.chat.adapters.persistence.jpa.identity;

import edu.artemiy.chat.identity.spi.PasswordResetNotification;
import edu.artemiy.chat.identity.spi.ResetNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "docker-local", "test"})
class LoggingResetNotificationAdapter implements ResetNotificationPort {

    private static final Logger logger = LoggerFactory.getLogger(LoggingResetNotificationAdapter.class);

    @Override
    public void sendPasswordReset(PasswordResetNotification notification) {
        logger.info(
                "Issued password reset token for userId={} email={} expiresAt={} resetUrl={}",
                notification.userId(),
                notification.email(),
                notification.expiresAt(),
                resetUrl(notification)
        );
    }

    private static String resetUrl(PasswordResetNotification notification) {
        String baseUrl = notification.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return notification.rawToken();
        }
        return "%s/password-reset/consume?token=%s".formatted(baseUrl.replaceAll("/+$", ""), notification.rawToken());
    }
}
