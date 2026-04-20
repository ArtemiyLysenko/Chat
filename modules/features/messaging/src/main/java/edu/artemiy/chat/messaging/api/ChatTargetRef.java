package edu.artemiy.chat.messaging.api;

import java.util.UUID;

public record ChatTargetRef(ChatTargetType type, UUID id) {
}
