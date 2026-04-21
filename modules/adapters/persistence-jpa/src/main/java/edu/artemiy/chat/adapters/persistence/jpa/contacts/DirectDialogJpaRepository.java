package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DirectDialogJpaRepository extends JpaRepository<DirectDialogEntity, UUID> {

    Optional<DirectDialogEntity> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);
}
