package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FederationTrafficSampleJpaRepository extends JpaRepository<FederationTrafficSampleEntity, UUID> {

    Optional<FederationTrafficSampleEntity> findFirstByPeer_IdOrderBySampledAtDesc(UUID peerId);

    @Query(value = """
        select
            coalesce(max(inbound_messages), 0) as inboundMessages,
            coalesce(max(outbound_messages), 0) as outboundMessages,
            coalesce(max(inbound_stanzas), 0) as inboundStanzas,
            coalesce(max(outbound_stanzas), 0) as outboundStanzas,
            coalesce(max(error_count), 0) as errorCount
        from federation_traffic_samples
        where peer_id = :peerId
        """, nativeQuery = true)
    FederationTrafficTotals findTotalsByPeerId(@Param("peerId") UUID peerId);

    List<FederationTrafficSampleEntity> findTop200ByOrderBySampledAtDesc();
}
