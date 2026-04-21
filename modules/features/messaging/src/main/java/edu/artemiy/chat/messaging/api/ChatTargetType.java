package edu.artemiy.chat.messaging.api;

import java.util.Locale;

public enum ChatTargetType {
    ROOM,
    DIRECT;

    public static ChatTargetType fromHttpValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Chat type is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported chat type: " + value);
        }
    }
}
