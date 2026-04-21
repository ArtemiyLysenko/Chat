package edu.artemiy.chat.contacts.api;

import java.util.List;

public record ContactsView(
    List<FriendContactSummary> friends,
    List<PendingFriendRequestSummary> inboundPendingRequests,
    List<PendingFriendRequestSummary> outboundPendingRequests,
    List<BlockedContactSummary> blockedUsers
) {
}
