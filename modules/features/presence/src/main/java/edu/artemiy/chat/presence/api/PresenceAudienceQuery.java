package edu.artemiy.chat.presence.api;

import java.util.Set;
import java.util.UUID;

public interface PresenceAudienceQuery {

    Set<UUID> listAudienceUserIds(UUID subjectUserId);
}
