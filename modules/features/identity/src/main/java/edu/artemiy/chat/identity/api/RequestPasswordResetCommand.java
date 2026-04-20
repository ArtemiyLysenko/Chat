package edu.artemiy.chat.identity.api;

public record RequestPasswordResetCommand(
    String email,
    String baseUrl
) {
}
