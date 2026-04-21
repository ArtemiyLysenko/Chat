package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import edu.artemiy.chat.attachments.spi.AttachmentPersistencePort;
import edu.artemiy.chat.attachments.spi.NewAttachmentRecord;
import edu.artemiy.chat.attachments.spi.NewMessageAttachmentRecord;
import edu.artemiy.chat.attachments.spi.StoredAttachment;
import edu.artemiy.chat.messaging.api.ChatTargetRef;

@Component
class JpaAttachmentPersistenceAdapter implements AttachmentPersistencePort {

    private final AttachmentJpaRepository attachmentJpaRepository;
    private final MessageAttachmentJpaRepository messageAttachmentJpaRepository;

    JpaAttachmentPersistenceAdapter(
        AttachmentJpaRepository attachmentJpaRepository,
        MessageAttachmentJpaRepository messageAttachmentJpaRepository
    ) {
        this.attachmentJpaRepository = attachmentJpaRepository;
        this.messageAttachmentJpaRepository = messageAttachmentJpaRepository;
    }

    @Override
    public StoredAttachment createAttachment(NewAttachmentRecord attachment) {
        attachmentJpaRepository.saveAndFlush(new AttachmentEntity(
            attachment.id(),
            attachment.storageKey(),
            attachment.originalName(),
            attachment.mediaType(),
            attachment.sizeBytes(),
            attachment.sha256(),
            attachment.uploadedByUserId(),
            attachment.chat().type(),
            attachment.chat().type() == edu.artemiy.chat.messaging.api.ChatTargetType.ROOM ? attachment.chat().id() : null,
            attachment.chat().type() == edu.artemiy.chat.messaging.api.ChatTargetType.DIRECT ? attachment.chat().id() : null,
            attachment.createdAt()
        ));
        return findAttachment(attachment.id()).orElseThrow();
    }

    @Override
    public void attachToMessage(NewMessageAttachmentRecord messageAttachment) {
        messageAttachmentJpaRepository.saveAndFlush(new MessageAttachmentEntity(
            messageAttachment.attachmentId(),
            messageAttachment.messageId(),
            messageAttachment.commentText(),
            messageAttachment.sortOrder()
        ));
    }

    @Override
    public Optional<StoredAttachment> findAttachment(UUID attachmentId) {
        return attachmentJpaRepository.findById(attachmentId).map(JpaAttachmentPersistenceAdapter::toStoredAttachment);
    }

    private static StoredAttachment toStoredAttachment(AttachmentEntity entity) {
        return new StoredAttachment(
            entity.getId(),
            entity.getStorageKey(),
            entity.getOriginalName(),
            entity.getMediaType(),
            entity.getSizeBytes(),
            entity.getSha256(),
            entity.getUploadedByUserId(),
            entity.getChatTargetType() == edu.artemiy.chat.messaging.api.ChatTargetType.ROOM
                ? new ChatTargetRef(edu.artemiy.chat.messaging.api.ChatTargetType.ROOM, entity.getRoomId())
                : new ChatTargetRef(edu.artemiy.chat.messaging.api.ChatTargetType.DIRECT, entity.getDirectDialogId()),
            entity.getCreatedAt()
        );
    }
}
