package edu.artemiy.chat.adapters.persistence.jpa.identity;

import edu.artemiy.chat.identity.spi.AccountDeletionImpact;
import edu.artemiy.chat.identity.spi.AccountDeletionImpactPort;

class NoOpAccountDeletionImpactAdapter implements AccountDeletionImpactPort {

    @Override
    public void handleAccountDeleted(AccountDeletionImpact impact) {
        // Cross-feature cleanup lands in later milestones.
    }
}
