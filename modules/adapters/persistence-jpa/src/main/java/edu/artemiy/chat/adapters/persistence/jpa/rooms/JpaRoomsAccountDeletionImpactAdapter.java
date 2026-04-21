package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.artemiy.chat.identity.spi.AccountDeletionImpact;
import edu.artemiy.chat.identity.spi.AccountDeletionImpactPort;

@Component
class JpaRoomsAccountDeletionImpactAdapter implements AccountDeletionImpactPort {

    private final RoomJpaRepository roomJpaRepository;
    private final RoomMembershipJpaRepository roomMembershipJpaRepository;
    private final RoomInviteJpaRepository roomInviteJpaRepository;
    private final RoomBanJpaRepository roomBanJpaRepository;

    JpaRoomsAccountDeletionImpactAdapter(
        RoomJpaRepository roomJpaRepository,
        RoomMembershipJpaRepository roomMembershipJpaRepository,
        RoomInviteJpaRepository roomInviteJpaRepository,
        RoomBanJpaRepository roomBanJpaRepository
    ) {
        this.roomJpaRepository = roomJpaRepository;
        this.roomMembershipJpaRepository = roomMembershipJpaRepository;
        this.roomInviteJpaRepository = roomInviteJpaRepository;
        this.roomBanJpaRepository = roomBanJpaRepository;
    }

    @Override
    @Transactional
    public void handleAccountDeleted(AccountDeletionImpact impact) {
        List<UUID> ownedRoomIds = roomJpaRepository.findAllByOwnerUserId(impact.userId()).stream()
            .map(RoomEntity::getId)
            .toList();
        if (!ownedRoomIds.isEmpty()) {
            roomJpaRepository.deleteAllByIdInBatch(ownedRoomIds);
            roomJpaRepository.flush();
        }

        roomMembershipJpaRepository.deleteByIdUserId(impact.userId());
        roomInviteJpaRepository.deleteByIdInvitedUserId(impact.userId());
        roomInviteJpaRepository.deleteByInvitedByUserId(impact.userId());
        roomBanJpaRepository.deleteByIdUserId(impact.userId());
    }
}
