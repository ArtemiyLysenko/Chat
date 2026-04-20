package edu.artemiy.chat.identity.api;

public record LoginCommand(
    String email,
    String password,
    ClientContext clientContext
) {
}
