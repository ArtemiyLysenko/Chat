package edu.artemiy.chat.messaging.api;

public enum MessageEventType {
    CREATED("message.created"),
    UPDATED("message.updated"),
    DELETED("message.deleted");

    private final String eventType;

    MessageEventType(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
