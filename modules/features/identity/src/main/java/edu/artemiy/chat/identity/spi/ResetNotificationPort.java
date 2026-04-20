package edu.artemiy.chat.identity.spi;

public interface ResetNotificationPort {

    void sendPasswordReset(PasswordResetNotification notification);
}
