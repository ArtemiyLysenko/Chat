package edu.artemiy.chat.attachments.spi;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentPersistencePort {

    StoredAttachment createAttachment(NewAttachmentRecord attachment);

    void attachToMessage(NewMessageAttachmentRecord messageAttachment);

    Optional<StoredAttachment> findAttachment(UUID attachmentId);
}
