package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FederationTrafficSampleJpaRepository extends JpaRepository<FederationTrafficSampleEntity, UUID> {

    Optional<FederationTrafficSampleEntity> findFirstByPeer_IdOrderBySampledAtDesc(UUID peerId);

    List<FederationTrafficSampleEntity> findTop200ByOrderBySampledAtDesc();
}
