package edu.artemiy.chat.app.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import edu.artemiy.chat.attachments.api.AttachmentDescriptor;
import edu.artemiy.chat.attachments.api.AttachmentDownload;
import edu.artemiy.chat.attachments.api.AttachmentsService;
import edu.artemiy.chat.attachments.api.UploadAttachmentCommand;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;

@RestController
class AttachmentsHttpController {

    private final AttachmentsService attachmentsService;

    AttachmentsHttpController(AttachmentsService attachmentsService) {
        this.attachmentsService = attachmentsService;
    }

    @PostMapping(path = "/api/chats/{chatType}/{chatId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttachmentDescriptor> uploadAttachment(
        @PathVariable String chatType,
        @PathVariable UUID chatId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String commentText,
        @RequestParam(required = false) UUID messageId,
        Authentication authentication
    ) throws IOException {
        AttachmentDescriptor descriptor = attachmentsService.uploadAttachment(
            AuthenticatedHttpUserSupport.userId(authentication),
            new UploadAttachmentCommand(
                chatTarget(chatType, chatId),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                commentText,
                messageId
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(descriptor);
    }

    @GetMapping("/api/attachments/{attachmentId}")
    AttachmentDescriptor readAttachment(@PathVariable UUID attachmentId, Authentication authentication) {
        return attachmentsService.readAttachment(AuthenticatedHttpUserSupport.userId(authentication), attachmentId);
    }

    @GetMapping("/api/attachments/{attachmentId}/download")
    ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID attachmentId, Authentication authentication) {
        AttachmentDownload download = attachmentsService.downloadAttachment(
            AuthenticatedHttpUserSupport.userId(authentication),
            attachmentId
        );
        return ResponseEntity.ok()
            .contentType(parseMediaType(download.descriptor().mediaType()))
            .contentLength(download.content().length)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.descriptor().originalName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(download.content());
    }

    private static ChatTargetRef chatTarget(String chatType, UUID chatId) {
        return new ChatTargetRef(ChatTargetType.fromHttpValue(chatType), chatId);
    }

    private static MediaType parseMediaType(String mediaType) {
        try {
            return MediaType.parseMediaType(mediaType);
        }
        catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
