package edu.artemiy.chat.attachments.api;

import java.util.UUID;

public interface AttachmentsService {

    AttachmentDescriptor uploadAttachment(UUID actorUserId, UploadAttachmentCommand command);

    AttachmentDescriptor readAttachment(UUID actorUserId, UUID attachmentId);

    AttachmentDownload downloadAttachment(UUID actorUserId, UUID attachmentId);
}
