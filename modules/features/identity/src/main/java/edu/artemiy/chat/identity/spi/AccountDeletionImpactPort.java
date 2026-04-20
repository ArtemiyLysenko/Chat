package edu.artemiy.chat.identity.spi;

public interface AccountDeletionImpactPort {

    void handleAccountDeleted(AccountDeletionImpact impact);
}
