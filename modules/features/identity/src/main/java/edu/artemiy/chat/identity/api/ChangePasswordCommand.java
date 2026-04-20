package edu.artemiy.chat.identity.api;

public record ChangePasswordCommand(
    String currentPassword,
    String newPassword
) {
}
