package edu.artemiy.chat.identity.api;

public record ClientContext(
    String userAgent,
    String ipAddress
) {
}
