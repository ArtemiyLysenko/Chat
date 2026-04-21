package edu.artemiy.chat.rooms.spi;

import java.time.Instant;
import java.util.UUID;

import edu.artemiy.chat.rooms.api.ModerationAction;

public record NewModerationAuditRecord(
    UUID roomId,
    UUID actorUserId,
    UUID targetUserId,
    ModerationAction action,
    String reason,
    String metadataJson,
    Instant createdAt
) {
}
