package edu.artemiy.chat.adapters.persistence.jpa.federation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FederationPeerJpaRepository extends JpaRepository<FederationPeerEntity, UUID> {

    Optional<FederationPeerEntity> findByPeerDomain(String peerDomain);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select peer from FederationPeerEntity peer where peer.peerDomain = :peerDomain")
    Optional<FederationPeerEntity> findByPeerDomainForUpdate(@Param("peerDomain") String peerDomain);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        insert into federation_peers (
            id,
            peer_domain,
            status,
            last_connected_at,
            last_error_at,
            config_json
        ) values (
            :id,
            :peerDomain,
            :status,
            null,
            null,
            :configJson
        )
        on conflict (peer_domain) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("peerDomain") String peerDomain,
        @Param("status") String status,
        @Param("configJson") String configJson
    );

    List<FederationPeerEntity> findAllByOrderByPeerDomainAsc();
}
