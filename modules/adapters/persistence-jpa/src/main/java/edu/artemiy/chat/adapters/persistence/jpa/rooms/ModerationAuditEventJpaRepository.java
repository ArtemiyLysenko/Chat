package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ModerationAuditEventJpaRepository extends JpaRepository<ModerationAuditEventEntity, UUID> {
}
