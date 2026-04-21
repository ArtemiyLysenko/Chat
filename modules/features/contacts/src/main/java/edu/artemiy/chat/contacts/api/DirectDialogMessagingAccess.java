package edu.artemiy.chat.contacts.api;

import java.util.Objects;
import java.util.UUID;

public record DirectDialogMessagingAccess(UUID dialogId, DirectDialogMessagingAccessStatus status, UUID otherUserId) {

    public DirectDialogMessagingAccess {
        Objects.requireNonNull(dialogId, "Direct dialog id is required.");
        Objects.requireNonNull(status, "Direct dialog messaging access status is required.");
    }
}
