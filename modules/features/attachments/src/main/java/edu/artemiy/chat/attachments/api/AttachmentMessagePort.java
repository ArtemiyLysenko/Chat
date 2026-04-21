package edu.artemiy.chat.attachments.api;

import java.util.Optional;
import java.util.UUID;

import edu.artemiy.chat.messaging.api.ChatMessage;

public interface AttachmentMessagePort {

    void createMessage(NewAttachmentMessageRecord message);

    Optional<ChatMessage> findMessage(UUID messageId);
}
