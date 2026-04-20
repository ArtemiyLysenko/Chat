package edu.artemiy.chat.core.kernel;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}
