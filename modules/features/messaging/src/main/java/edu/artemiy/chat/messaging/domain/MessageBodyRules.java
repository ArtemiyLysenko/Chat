package edu.artemiy.chat.messaging.domain;

import java.nio.charset.StandardCharsets;

import edu.artemiy.chat.messaging.api.MessagingErrorType;
import edu.artemiy.chat.messaging.api.MessagingException;

public final class MessageBodyRules {

    public static final int MAX_UTF8_BYTES = 3 * 1024;

    private MessageBodyRules() {
    }

    public static String requireValid(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            throw new MessagingException(
                "messaging.message_body_required",
                "Message body is required.",
                MessagingErrorType.BAD_REQUEST
            );
        }
        if (bodyText.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new MessagingException(
                "messaging.message_body_too_large",
                "Message body must not exceed 3 KB in UTF-8.",
                MessagingErrorType.BAD_REQUEST
            );
        }
        return bodyText;
    }
}
