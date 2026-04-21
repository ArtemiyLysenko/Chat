package edu.artemiy.chat.contacts.api;

import java.time.Instant;
import java.util.UUID;

public record FriendContactSummary(
    UUID friendshipId,
    ContactUserSummary user,
    UUID directDialogId,
    Instant friendsSince
) {
}
