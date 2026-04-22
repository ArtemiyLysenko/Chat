package edu.artemiy.chat.identity.api;

public record UsernamePasswordAuthenticationCommand(
    String username,
    String password
) {
}
