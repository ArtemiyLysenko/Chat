package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public interface ContactsService {

    ContactsView listContacts(UUID actorUserId);

    FriendRequestSubmission createFriendRequest(UUID actorUserId, CreateFriendRequestCommand command);

    void acceptFriendRequest(UUID actorUserId, UUID requestId);

    void rejectFriendRequest(UUID actorUserId, UUID requestId);

    void removeFriend(UUID actorUserId, UUID userId);

    void blockUser(UUID actorUserId, UUID userId);

    void unblockUser(UUID actorUserId, UUID userId);

    DirectMessageEligibility evaluateDirectMessageEligibility(UUID actorUserId, UUID userId);
}
