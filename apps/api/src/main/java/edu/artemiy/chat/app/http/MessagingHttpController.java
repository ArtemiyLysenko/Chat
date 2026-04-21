package edu.artemiy.chat.app.http;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.messaging.api.AdvanceReadMarkerCommand;
import edu.artemiy.chat.messaging.api.ChatMessage;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.EditMessageCommand;
import edu.artemiy.chat.messaging.api.MessageHistoryPage;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.messaging.api.SendMessageCommand;
import edu.artemiy.chat.messaging.api.UnreadMarker;

@RestController
@Validated
class MessagingHttpController {

    private final MessagingService messagingService;

    MessagingHttpController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/api/chats/{chatType}/{chatId}/messages")
    MessageHistoryPage readMessageHistory(
        @PathVariable String chatType,
        @PathVariable UUID chatId,
        @RequestParam(required = false) UUID before,
        @RequestParam(defaultValue = "50") @Min(1) @Max(ReadMessageHistoryQuery.MAX_LIMIT) int limit,
        Authentication authentication
    ) {
        return messagingService.readMessageHistory(
            AuthenticatedHttpUserSupport.userId(authentication),
            new ReadMessageHistoryQuery(chatTarget(chatType, chatId), before, limit)
        );
    }

    @PostMapping("/api/chats/{chatType}/{chatId}/messages")
    ResponseEntity<ChatMessage> sendMessage(
        @PathVariable String chatType,
        @PathVariable UUID chatId,
        @Valid @RequestBody SendMessageRequest request,
        Authentication authentication
    ) {
        ChatMessage createdMessage = messagingService.sendMessage(
            AuthenticatedHttpUserSupport.userId(authentication),
            new SendMessageCommand(chatTarget(chatType, chatId), request.bodyText(), request.parentMessageId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMessage);
    }

    @PatchMapping("/api/messages/{messageId}")
    ChatMessage editMessage(
        @PathVariable UUID messageId,
        @Valid @RequestBody EditMessageRequest request,
        Authentication authentication
    ) {
        return messagingService.editMessage(
            AuthenticatedHttpUserSupport.userId(authentication),
            new EditMessageCommand(messageId, request.bodyText())
        );
    }

    @DeleteMapping("/api/messages/{messageId}")
    ChatMessage deleteMessage(@PathVariable UUID messageId, Authentication authentication) {
        return messagingService.deleteMessage(AuthenticatedHttpUserSupport.userId(authentication), messageId);
    }

    @PostMapping("/api/chats/{chatType}/{chatId}/read-markers")
    UnreadMarker advanceReadMarker(
        @PathVariable String chatType,
        @PathVariable UUID chatId,
        @Valid @RequestBody AdvanceReadMarkerRequest request,
        Authentication authentication
    ) {
        return messagingService.advanceReadMarker(
            AuthenticatedHttpUserSupport.userId(authentication),
            new AdvanceReadMarkerCommand(chatTarget(chatType, chatId), request.lastReadMessageId())
        );
    }

    private static ChatTargetRef chatTarget(String chatType, UUID chatId) {
        return new ChatTargetRef(ChatTargetType.fromHttpValue(chatType), chatId);
    }

    private record SendMessageRequest(@NotNull String bodyText, UUID parentMessageId) {
    }

    private record EditMessageRequest(@NotNull String bodyText) {
    }

    private record AdvanceReadMarkerRequest(@NotNull UUID lastReadMessageId) {
    }
}
