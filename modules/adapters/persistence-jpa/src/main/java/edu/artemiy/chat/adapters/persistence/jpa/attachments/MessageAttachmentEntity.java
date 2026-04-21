package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_attachments")
class MessageAttachmentEntity {

    @Id
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "comment_text", columnDefinition = "text")
    private String commentText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MessageAttachmentEntity() {
    }

    MessageAttachmentEntity(UUID attachmentId, UUID messageId, String commentText, int sortOrder) {
        this.attachmentId = attachmentId;
        this.messageId = messageId;
        this.commentText = commentText;
        this.sortOrder = sortOrder;
    }

    UUID getAttachmentId() {
        return attachmentId;
    }

    UUID getMessageId() {
        return messageId;
    }

    String getCommentText() {
        return commentText;
    }

    int getSortOrder() {
        return sortOrder;
    }
}
