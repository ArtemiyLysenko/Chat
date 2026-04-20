package edu.artemiy.chat.adapters.persistence.jpa.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.identity.spi.PasswordResetNotification;
import edu.artemiy.chat.identity.spi.ResetNotificationPort;

@Component
@Profile("!local & !docker-local & !test")
class NoOpResetNotificationAdapter implements ResetNotificationPort {

    @Override
    public void sendPasswordReset(PasswordResetNotification notification) {
        // Production-grade delivery lands after Milestone 1.
    }
}
