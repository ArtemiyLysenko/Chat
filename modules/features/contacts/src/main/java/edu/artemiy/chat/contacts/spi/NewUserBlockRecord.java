package edu.artemiy.chat.contacts.spi;

import java.time.Instant;
import java.util.UUID;

public record NewUserBlockRecord(
    UUID id,
    UUID blockerUserId,
    UUID blockedUserId,
    Instant createdAt
) {
}
