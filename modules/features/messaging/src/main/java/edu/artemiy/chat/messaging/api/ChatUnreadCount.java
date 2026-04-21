package edu.artemiy.chat.messaging.api;

import java.util.Objects;

public record ChatUnreadCount(ChatTargetRef chat, int unreadCount) {

    public ChatUnreadCount {
        Objects.requireNonNull(chat, "Chat target is required.");
        if (unreadCount < 0) {
            throw new IllegalArgumentException("Unread count cannot be negative.");
        }
    }
}
