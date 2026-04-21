package edu.artemiy.chat.contacts.domain;

import java.util.UUID;

public record OrderedContactPair(UUID lowUserId, UUID highUserId) {

    public static OrderedContactPair of(UUID firstUserId, UUID secondUserId) {
        return compareUuid(firstUserId, secondUserId) <= 0
            ? new OrderedContactPair(firstUserId, secondUserId)
            : new OrderedContactPair(secondUserId, firstUserId);
    }

    private static int compareUuid(UUID firstUserId, UUID secondUserId) {
        int highBitsComparison = Long.compareUnsigned(firstUserId.getMostSignificantBits(), secondUserId.getMostSignificantBits());
        return highBitsComparison != 0
            ? highBitsComparison
            : Long.compareUnsigned(firstUserId.getLeastSignificantBits(), secondUserId.getLeastSignificantBits());
    }
}
