package edu.artemiy.chat.contacts.domain;

import edu.artemiy.chat.contacts.api.DirectMessageEligibility;

public record ContactRelationshipState(
    boolean activeFriendship,
    boolean blockedByActor,
    boolean blockedByOtherUser
) {

    public boolean hasAnyBlock() {
        return blockedByActor || blockedByOtherUser;
    }

    public boolean allowsNewFriendRequest() {
        return !activeFriendship && !hasAnyBlock();
    }

    public DirectMessageEligibility directMessageEligibility() {
        if (hasAnyBlock()) {
            return DirectMessageEligibility.BLOCKED;
        }
        return activeFriendship ? DirectMessageEligibility.ELIGIBLE : DirectMessageEligibility.NOT_FRIENDS;
    }
}
