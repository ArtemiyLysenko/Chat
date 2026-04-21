package edu.artemiy.chat.adapters.persistence.jpa.attachments;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AttachmentJpaRepository extends JpaRepository<AttachmentEntity, UUID> {
}
