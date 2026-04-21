package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import edu.artemiy.chat.attachments.api.AttachmentMessagePort;
import edu.artemiy.chat.attachments.api.NewAttachmentMessageRecord;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.MessageAttachment;
import edu.artemiy.chat.messaging.api.MessageAuthor;
import edu.artemiy.chat.messaging.api.MessageReplyTarget;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessage;
import edu.artemiy.chat.messaging.spi.StoredMessageAttachment;
import edu.artemiy.chat.messaging.spi.StoredMessageReplyTarget;

@Component
class JpaAttachmentMessagePort implements AttachmentMessagePort {

    private final MessagingPersistencePort messagingPersistencePort;

    JpaAttachmentMessagePort(MessagingPersistencePort messagingPersistencePort) {
        this.messagingPersistencePort = messagingPersistencePort;
    }

    @Override
    public void createMessage(NewAttachmentMessageRecord message) {
        messagingPersistencePort.createMessage(new NewMessageRecord(
            message.id(),
            message.chat(),
            message.authorUserId(),
            null,
            message.bodyText(),
            MessageState.ACTIVE,
            message.createdAt()
        ));
    }

    @Override
    public Optional<ChatMessage> findMessage(UUID messageId) {
        return messagingPersistencePort.findMessage(messageId).map(JpaAttachmentMessagePort::toChatMessage);
    }

    private static ChatMessage toChatMessage(StoredMessage storedMessage) {
        return new ChatMessage(
            storedMessage.id(),
            storedMessage.chat(),
            new MessageAuthor(
                storedMessage.author().id(),
                storedMessage.author().username(),
                storedMessage.author().displayName(),
                storedMessage.author().deleted()
            ),
            storedMessage.state() == MessageState.DELETED ? "" : storedMessage.bodyText(),
            storedMessage.state(),
            storedMessage.createdAt(),
            storedMessage.editedAt(),
            toReplyTarget(storedMessage.replyTo()),
            storedMessage.attachments().stream().map(JpaAttachmentMessagePort::toMessageAttachment).toList()
        );
    }

    private static MessageReplyTarget toReplyTarget(StoredMessageReplyTarget replyTarget) {
        if (replyTarget == null) {
            return null;
        }
        return new MessageReplyTarget(
            replyTarget.messageId(),
            new MessageAuthor(
                replyTarget.author().id(),
                replyTarget.author().username(),
                replyTarget.author().displayName(),
                replyTarget.author().deleted()
            ),
            replyTarget.state() == MessageState.DELETED ? "" : replyTarget.bodyText(),
            replyTarget.state()
        );
    }

    private static MessageAttachment toMessageAttachment(StoredMessageAttachment attachment) {
        return new MessageAttachment(
            attachment.attachmentId(),
            attachment.originalName(),
            attachment.mediaType(),
            attachment.sizeBytes(),
            attachment.commentText(),
            attachment.sortOrder()
        );
    }
}
