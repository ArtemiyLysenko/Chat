package edu.artemiy.chat.messaging.api;

import java.util.Set;
import java.util.UUID;

public interface ChatAudienceQuery {

    Set<UUID> listAudienceUserIds(ChatTargetRef chat);
}
