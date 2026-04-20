package edu.artemiy.chat.identity.spi;

public final class DuplicateUserIdentityException extends RuntimeException {

    private final ConflictTarget conflictTarget;

    public DuplicateUserIdentityException(ConflictTarget conflictTarget, Throwable cause) {
        super("User identity already exists: " + conflictTarget.name().toLowerCase(), cause);
        this.conflictTarget = conflictTarget;
    }

    public ConflictTarget conflictTarget() {
        return conflictTarget;
    }

    public enum ConflictTarget {
        EMAIL,
        USERNAME,
        UNKNOWN
    }
}
