package edu.artemiy.chat.contacts.api;

import java.time.Instant;

public record BlockedContactSummary(
    ContactUserSummary user,
    Instant blockedAt
) {
}
