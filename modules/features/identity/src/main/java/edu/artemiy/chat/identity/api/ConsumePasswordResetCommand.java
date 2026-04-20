package edu.artemiy.chat.identity.api;

public record ConsumePasswordResetCommand(
    String token,
    String newPassword
) {
}
