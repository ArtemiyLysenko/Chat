package edu.artemiy.chat.attachments.application;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import edu.artemiy.chat.attachments.api.AttachmentDescriptor;
import edu.artemiy.chat.attachments.api.AttachmentDownload;
import edu.artemiy.chat.attachments.api.AttachmentsErrorType;
import edu.artemiy.chat.attachments.api.AttachmentsException;
import edu.artemiy.chat.attachments.api.AttachmentMessagePort;
import edu.artemiy.chat.attachments.api.AttachmentsService;
import edu.artemiy.chat.attachments.api.NewAttachmentMessageRecord;
import edu.artemiy.chat.attachments.api.UploadAttachmentCommand;
import edu.artemiy.chat.attachments.domain.AttachmentUploadRules;
import edu.artemiy.chat.attachments.spi.AttachmentPersistencePort;
import edu.artemiy.chat.attachments.spi.AttachmentStoragePort;
import edu.artemiy.chat.attachments.spi.NewAttachmentRecord;
import edu.artemiy.chat.attachments.spi.NewMessageAttachmentRecord;
import edu.artemiy.chat.attachments.spi.StoredAttachment;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccess;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessQuery;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessStatus;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.core.kernel.ClockPort;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.ChatMessageEvent;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageEventType;
import edu.artemiy.chat.rooms.api.RoomMessagingAccess;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessQuery;
import edu.artemiy.chat.rooms.api.RoomMessagingAccessStatus;

@Service
public class DefaultAttachmentsService implements AttachmentsService {

    private final ClockPort clockPort;
    private final AttachmentPersistencePort attachmentPersistencePort;
    private final AttachmentStoragePort attachmentStoragePort;
    private final AttachmentMessagePort attachmentMessagePort;
    private final RoomMessagingAccessQuery roomMessagingAccessQuery;
    private final DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery;
    private final ContactsService contactsService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DefaultAttachmentsService(
        ClockPort clockPort,
        AttachmentPersistencePort attachmentPersistencePort,
        AttachmentStoragePort attachmentStoragePort,
        AttachmentMessagePort attachmentMessagePort,
        RoomMessagingAccessQuery roomMessagingAccessQuery,
        DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery,
        ContactsService contactsService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.clockPort = clockPort;
        this.attachmentPersistencePort = attachmentPersistencePort;
        this.attachmentStoragePort = attachmentStoragePort;
        this.attachmentMessagePort = attachmentMessagePort;
        this.roomMessagingAccessQuery = roomMessagingAccessQuery;
        this.directDialogMessagingAccessQuery = directDialogMessagingAccessQuery;
        this.contactsService = contactsService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @Transactional
    public AttachmentDescriptor uploadAttachment(UUID actorUserId, UploadAttachmentCommand command) {
        if (command.messageId() != null) {
            throw new AttachmentsException(
                "attachments.message_binding_unsupported",
                "Attaching uploads to an existing message is not supported in this milestone.",
                AttachmentsErrorType.BAD_REQUEST
            );
        }

        ChatTargetRef chat = command.chat();
        requireUploadAccess(actorUserId, chat);

        byte[] content = AttachmentUploadRules.requireContent(command.content());
        String mediaType = AttachmentUploadRules.normalizeMediaType(command.mediaType());
        AttachmentUploadRules.requireValidSize(content.length, mediaType);
        String originalName = AttachmentUploadRules.normalizeOriginalName(command.originalName());
        String commentText = AttachmentUploadRules.normalizeComment(command.commentText());
        String messageBody = commentText == null
            ? AttachmentUploadRules.defaultMessageBody(originalName)
            : commentText;
        java.time.Instant now = clockPort.now();

        UUID messageId = UUID.randomUUID();
        attachmentMessagePort.createMessage(new NewAttachmentMessageRecord(
            messageId,
            chat,
            actorUserId,
            messageBody,
            now
        ));

        UUID attachmentId = UUID.randomUUID();
        StoredAttachment attachment = attachmentPersistencePort.createAttachment(new NewAttachmentRecord(
            attachmentId,
            storageKey(attachmentId),
            originalName,
            mediaType,
            content.length,
            AttachmentUploadRules.sha256Hex(content),
            actorUserId,
            chat,
            now
        ));
        attachmentPersistencePort.attachToMessage(new NewMessageAttachmentRecord(
            messageId,
            attachment.id(),
            commentText,
            0
        ));
        storeAttachmentBinary(attachment.storageKey(), content);

        ChatMessage createdMessage = attachmentMessagePort.findMessage(messageId).orElseThrow();
        applicationEventPublisher.publishEvent(new ChatMessageEvent(
            actorUserId,
            MessageEventType.CREATED,
            createdMessage,
            now
        ));

        return toDescriptor(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDescriptor readAttachment(UUID actorUserId, UUID attachmentId) {
        StoredAttachment attachment = requireReadableAttachment(actorUserId, attachmentId);
        return toDescriptor(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(UUID actorUserId, UUID attachmentId) {
        StoredAttachment attachment = requireReadableAttachment(actorUserId, attachmentId);
        if (!attachmentStoragePort.exists(attachment.storageKey())) {
            throw new AttachmentsException(
                "attachments.binary_missing",
                "Attachment content is unavailable.",
                AttachmentsErrorType.CONFLICT
            );
        }
        return new AttachmentDownload(toDescriptor(attachment), attachmentStoragePort.read(attachment.storageKey()));
    }

    private StoredAttachment requireReadableAttachment(UUID actorUserId, UUID attachmentId) {
        StoredAttachment attachment = attachmentPersistencePort.findAttachment(attachmentId).orElseThrow(() -> new AttachmentsException(
            "attachments.not_found",
            "Attachment was not found.",
            AttachmentsErrorType.NOT_FOUND
        ));
        requireReadAccess(actorUserId, attachment.chat());
        return attachment;
    }

    private void requireUploadAccess(UUID actorUserId, ChatTargetRef chat) {
        switch (chat.type()) {
            case ROOM -> requireRoomAccess(actorUserId, chat.id());
            case DIRECT -> {
                UUID otherUserId = requireDirectDialogAccess(actorUserId, chat.id());
                requireDirectSendEligibility(actorUserId, otherUserId);
            }
        }
    }

    private void requireReadAccess(UUID actorUserId, ChatTargetRef chat) {
        switch (chat.type()) {
            case ROOM -> requireRoomAccess(actorUserId, chat.id());
            case DIRECT -> requireDirectDialogAccess(actorUserId, chat.id());
        }
    }

    private void requireRoomAccess(UUID actorUserId, UUID roomId) {
        RoomMessagingAccess access = roomMessagingAccessQuery.evaluateRoomMessagingAccess(actorUserId, roomId);
        switch (access.status()) {
            case ALLOWED -> {
                return;
            }
            case ROOM_NOT_FOUND -> throw new AttachmentsException(
                "attachments.room_not_found",
                "Room was not found.",
                AttachmentsErrorType.NOT_FOUND
            );
            case NOT_MEMBER -> throw new AttachmentsException(
                "attachments.room_membership_required",
                "Current room membership is required.",
                AttachmentsErrorType.FORBIDDEN
            );
            case BANNED -> throw new AttachmentsException(
                "attachments.room_banned",
                "Banned users cannot access room attachments.",
                AttachmentsErrorType.FORBIDDEN
            );
        }
    }

    private UUID requireDirectDialogAccess(UUID actorUserId, UUID directDialogId) {
        DirectDialogMessagingAccess access = directDialogMessagingAccessQuery.evaluateDirectDialogMessagingAccess(actorUserId, directDialogId);
        return switch (access.status()) {
            case ALLOWED -> access.otherUserId();
            case DIRECT_DIALOG_NOT_FOUND -> throw new AttachmentsException(
                "attachments.direct_dialog_not_found",
                "Direct dialog was not found.",
                AttachmentsErrorType.NOT_FOUND
            );
            case NOT_PARTICIPANT -> throw new AttachmentsException(
                "attachments.direct_dialog_forbidden",
                "Only dialog participants can access direct-message attachments.",
                AttachmentsErrorType.FORBIDDEN
            );
        };
    }

    private void requireDirectSendEligibility(UUID actorUserId, UUID otherUserId) {
        switch (contactsService.evaluateDirectMessageEligibility(actorUserId, otherUserId)) {
            case ELIGIBLE -> {
                return;
            }
            case NOT_FRIENDS -> throw new AttachmentsException(
                "attachments.direct_dialog_not_friends",
                "Direct attachments require an active friendship.",
                AttachmentsErrorType.FORBIDDEN
            );
            case BLOCKED -> throw new AttachmentsException(
                "attachments.direct_dialog_blocked",
                "Direct attachments are unavailable while a block exists between these users.",
                AttachmentsErrorType.FORBIDDEN
            );
        }
    }

    private static AttachmentDescriptor toDescriptor(StoredAttachment attachment) {
        return new AttachmentDescriptor(
            attachment.id().toString(),
            attachment.storageKey(),
            attachment.originalName(),
            attachment.mediaType(),
            attachment.sizeBytes()
        );
    }

    private static String storageKey(UUID attachmentId) {
        return "uploads/" + attachmentId;
    }

    private void storeAttachmentBinary(String storageKey, byte[] content) {
        attachmentStoragePort.store(storageKey, content);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    attachmentStoragePort.delete(storageKey);
                }
                catch (RuntimeException ignored) {
                }
            }
        });
    }
}
