package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public record CreateFriendRequestCommand(
    UUID userId,
    String username,
    String messageText
) {
}
