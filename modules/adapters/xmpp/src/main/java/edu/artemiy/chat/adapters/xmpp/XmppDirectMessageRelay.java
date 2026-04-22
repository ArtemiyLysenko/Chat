package edu.artemiy.chat.adapters.xmpp;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccess;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessQuery;
import edu.artemiy.chat.contacts.api.DirectDialogMessagingAccessStatus;
import edu.artemiy.chat.messaging.api.ChatMessageEvent;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageEventType;

@Component
class XmppDirectMessageRelay {

    private final XmppProperties properties;
    private final XmppSessionRegistry sessionRegistry;
    private final DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery;

    XmppDirectMessageRelay(
        XmppProperties properties,
        XmppSessionRegistry sessionRegistry,
        DirectDialogMessagingAccessQuery directDialogMessagingAccessQuery
    ) {
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.directDialogMessagingAccessQuery = directDialogMessagingAccessQuery;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void relayChatMessage(ChatMessageEvent event) {
        if (event.type() != MessageEventType.CREATED || event.message().chat().type() != ChatTargetType.DIRECT) {
            return;
        }
        if (event.message().bodyText() == null || event.message().bodyText().isBlank()) {
            return;
        }

        DirectDialogMessagingAccess access = directDialogMessagingAccessQuery.evaluateDirectDialogMessagingAccess(
            event.actorUserId(),
            event.message().chat().id()
        );
        if (access.status() != DirectDialogMessagingAccessStatus.ALLOWED || access.otherUserId() == null) {
            return;
        }

        sessionRegistry.sendDirectMessage(
            access.otherUserId(),
            event.message().author().username() + "@" + properties.getDomain(),
            event.message().id(),
            event.message().bodyText()
        );
    }
}
