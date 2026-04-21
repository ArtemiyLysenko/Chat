package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public interface ContactsService {

    ContactsView listContacts(UUID actorUserId);

    FriendRequestSubmission createFriendRequest(UUID actorUserId, CreateFriendRequestCommand command);

    void acceptFriendRequest(UUID actorUserId, UUID requestId);

    void rejectFriendRequest(UUID actorUserId, UUID requestId);
}
