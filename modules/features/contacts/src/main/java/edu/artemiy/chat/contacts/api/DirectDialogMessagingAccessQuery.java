package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public interface DirectDialogMessagingAccessQuery {

    DirectDialogMessagingAccess evaluateDirectDialogMessagingAccess(UUID actorUserId, UUID directDialogId);
}
