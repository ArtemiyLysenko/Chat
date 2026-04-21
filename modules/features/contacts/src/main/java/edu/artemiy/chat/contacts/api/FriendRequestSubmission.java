package edu.artemiy.chat.contacts.api;

import java.util.UUID;

public record FriendRequestSubmission(
    FriendRequestSubmissionOutcome outcome,
    UUID requestId,
    UUID friendshipId,
    ContactUserSummary user
) {
}
