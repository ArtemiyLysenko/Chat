package edu.artemiy.chat.identity.api;

public record RegisterUserCommand(
    String email,
    String username,
    String password
) {
}
