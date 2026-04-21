package edu.artemiy.chat.contacts.api;

import java.time.Instant;
import java.util.UUID;

public record DirectDialogSummary(
    UUID dialogId,
    ContactUserSummary participant,
    Instant createdAt,
    boolean created
) {
}
