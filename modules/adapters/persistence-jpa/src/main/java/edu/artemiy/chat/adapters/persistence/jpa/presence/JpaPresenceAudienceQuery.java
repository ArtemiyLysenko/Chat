package edu.artemiy.chat.adapters.persistence.jpa.presence;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.presence.api.PresenceAudienceQuery;

@Component
class JpaPresenceAudienceQuery implements PresenceAudienceQuery {

    private final PresenceAudienceJpaRepository presenceAudienceJpaRepository;

    JpaPresenceAudienceQuery(PresenceAudienceJpaRepository presenceAudienceJpaRepository) {
        this.presenceAudienceJpaRepository = presenceAudienceJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> listAudienceUserIds(UUID subjectUserId) {
        return presenceAudienceJpaRepository.findAudienceUserIds(subjectUserId);
    }
}
