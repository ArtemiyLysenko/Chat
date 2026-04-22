package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FederationPeerJpaRepository extends JpaRepository<FederationPeerEntity, UUID> {

    Optional<FederationPeerEntity> findByPeerDomain(String peerDomain);

    List<FederationPeerEntity> findAllByOrderByPeerDomainAsc();
}
