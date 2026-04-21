package edu.artemiy.chat.adapters.persistence.jpa.contacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.DuplicateDirectDialogException;
import edu.artemiy.chat.contacts.spi.DuplicateFriendshipException;
import edu.artemiy.chat.contacts.spi.DuplicatePendingFriendRequestException;
import edu.artemiy.chat.contacts.spi.DuplicateUserBlockException;
import edu.artemiy.chat.contacts.spi.FriendshipRequestStatus;
import edu.artemiy.chat.contacts.spi.NewDirectDialogRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRecord;
import edu.artemiy.chat.contacts.spi.NewFriendshipRequestRecord;
import edu.artemiy.chat.contacts.spi.NewUserBlockRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class ContactsRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-21T14:00:00Z");

    @Autowired
    private ContactsPersistencePort contactsPersistencePort;

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private FriendshipRequestJpaRepository friendshipRequestJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbcTemplate.execute(
            """
                truncate table
                    direct_dialogs,
                    user_blocks,
                    friendships,
                    friendship_requests,
                    moderation_audit_events,
                    room_bans,
                    room_invites,
                    room_memberships,
                    rooms,
                    user_sessions,
                    password_reset_tokens,
                    users
                cascade
                """
        );
    }

    @Test
    void enforcesFriendshipPairUniqueness() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID lowUserId = compareUuid(captainId, scoutId) <= 0 ? captainId : scoutId;
        UUID highUserId = lowUserId.equals(captainId) ? scoutId : captainId;

        contactsPersistencePort.createFriendship(new NewFriendshipRecord(UUID.randomUUID(), lowUserId, highUserId, NOW));

        assertThatThrownBy(() -> contactsPersistencePort.createFriendship(
            new NewFriendshipRecord(UUID.randomUUID(), lowUserId, highUserId, NOW.plusSeconds(5))
        )).isInstanceOf(DuplicateFriendshipException.class);
    }

    @Test
    void enforcesPendingRequestUniquenessPerDirection() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");

        contactsPersistencePort.createFriendshipRequest(new NewFriendshipRequestRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            "First ping",
            NOW
        ));

        assertThatThrownBy(() -> contactsPersistencePort.createFriendshipRequest(new NewFriendshipRequestRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            "Second ping",
            NOW.plusSeconds(5)
        ))).isInstanceOf(DuplicatePendingFriendRequestException.class);
    }

    @Test
    void enforcesUserBlockUniquenessPerDirection() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");

        contactsPersistencePort.createUserBlock(new NewUserBlockRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            NOW
        ));

        assertThatThrownBy(() -> contactsPersistencePort.createUserBlock(new NewUserBlockRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            NOW.plusSeconds(5)
        ))).isInstanceOf(DuplicateUserBlockException.class);
    }

    @Test
    void enforcesDirectDialogPairUniqueness() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID lowUserId = compareUuid(captainId, scoutId) <= 0 ? captainId : scoutId;
        UUID highUserId = lowUserId.equals(captainId) ? scoutId : captainId;

        contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(UUID.randomUUID(), lowUserId, highUserId, NOW));

        assertThatThrownBy(() -> contactsPersistencePort.createDirectDialog(
            new NewDirectDialogRecord(UUID.randomUUID(), lowUserId, highUserId, NOW.plusSeconds(5))
        )).isInstanceOf(DuplicateDirectDialogException.class);
    }

    @Test
    void persistsAndListsBlocks() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");

        var block = contactsPersistencePort.createUserBlock(new NewUserBlockRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            NOW
        ));

        assertThat(contactsPersistencePort.findUserBlock(captainId, scoutId))
            .hasValueSatisfying(storedBlock -> {
                assertThat(storedBlock.id()).isEqualTo(block.id());
                assertThat(storedBlock.createdAt()).isEqualTo(NOW);
            });
        assertThat(contactsPersistencePort.listBlockedUsers(captainId)).singleElement()
            .satisfies(blockedUser -> {
                assertThat(blockedUser.otherUserId()).isEqualTo(scoutId);
                assertThat(blockedUser.otherUsername()).isEqualTo("scout");
                assertThat(blockedUser.blockedAt()).isEqualTo(NOW);
            });
    }

    @Test
    void deletesFriendshipByIdentifier() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID lowUserId = compareUuid(captainId, scoutId) <= 0 ? captainId : scoutId;
        UUID highUserId = lowUserId.equals(captainId) ? scoutId : captainId;

        var friendship = contactsPersistencePort.createFriendship(new NewFriendshipRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW
        ));

        contactsPersistencePort.deleteFriendship(friendship.id());

        assertThat(contactsPersistencePort.findFriendship(lowUserId, highUserId)).isEmpty();
    }

    @Test
    void findsStableDirectDialogForExistingPair() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID lowUserId = compareUuid(captainId, scoutId) <= 0 ? captainId : scoutId;
        UUID highUserId = lowUserId.equals(captainId) ? scoutId : captainId;

        var dialog = contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW
        ));

        assertThat(contactsPersistencePort.findDirectDialog(lowUserId, highUserId))
            .hasValueSatisfying(storedDialog -> {
                assertThat(storedDialog.id()).isEqualTo(dialog.id());
                assertThat(storedDialog.createdAt()).isEqualTo(NOW);
            });
        assertThat(jdbcTemplate.queryForObject("select count(*) from direct_dialogs where user_low_id = ? and user_high_id = ?", Integer.class, lowUserId, highUserId))
            .isEqualTo(1);
    }

    @Test
    void persistsAndTransitionsFriendshipRequests() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");

        var firstRequest = contactsPersistencePort.createFriendshipRequest(new NewFriendshipRequestRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            "Hello there",
            NOW
        ));

        assertThat(contactsPersistencePort.listOutboundPendingRequests(captainId)).singleElement()
            .extracting(request -> request.requestId())
            .isEqualTo(firstRequest.id());
        assertThat(contactsPersistencePort.listInboundPendingRequests(scoutId)).singleElement()
            .extracting(request -> request.requestId())
            .isEqualTo(firstRequest.id());

        contactsPersistencePort.markFriendshipRequestAccepted(firstRequest.id(), NOW.plusSeconds(10));

        assertThat(friendshipRequestJpaRepository.findById(firstRequest.id()))
            .hasValueSatisfying(request -> {
                assertThat(request.getStatus()).isEqualTo(FriendshipRequestStatus.ACCEPTED);
                assertThat(request.getRespondedAt()).isEqualTo(NOW.plusSeconds(10));
            });
        assertThat(contactsPersistencePort.listOutboundPendingRequests(captainId)).isEmpty();
        assertThat(contactsPersistencePort.listInboundPendingRequests(scoutId)).isEmpty();

        var secondRequest = contactsPersistencePort.createFriendshipRequest(new NewFriendshipRequestRecord(
            UUID.randomUUID(),
            captainId,
            scoutId,
            "Try again later",
            NOW.plusSeconds(20)
        ));
        contactsPersistencePort.markFriendshipRequestRejected(secondRequest.id(), NOW.plusSeconds(30));

        assertThat(friendshipRequestJpaRepository.findById(secondRequest.id()))
            .hasValueSatisfying(request -> {
                assertThat(request.getStatus()).isEqualTo(FriendshipRequestStatus.REJECTED);
                assertThat(request.getRespondedAt()).isEqualTo(NOW.plusSeconds(30));
            });
        assertThat(contactsPersistencePort.listOutboundPendingRequests(captainId)).isEmpty();
        assertThat(contactsPersistencePort.listInboundPendingRequests(scoutId)).isEmpty();
    }

    @Test
    void preservesDirectDialogRowsAfterRelationshipCleanup() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID lowUserId = compareUuid(captainId, scoutId) <= 0 ? captainId : scoutId;
        UUID highUserId = lowUserId.equals(captainId) ? scoutId : captainId;

        var friendship = contactsPersistencePort.createFriendship(new NewFriendshipRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW.minusSeconds(30)
        ));
        var dialog = contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW
        ));

        contactsPersistencePort.deleteFriendship(friendship.id());
        contactsPersistencePort.createUserBlock(new NewUserBlockRecord(UUID.randomUUID(), captainId, scoutId, NOW.plusSeconds(5)));
        contactsPersistencePort.deleteRelationshipsForDeletedUser(captainId);

        assertThat(contactsPersistencePort.findDirectDialog(lowUserId, highUserId))
            .hasValueSatisfying(storedDialog -> assertThat(storedDialog.id()).isEqualTo(dialog.id()));
        assertThat(jdbcTemplate.queryForObject("select count(*) from direct_dialogs where id = ?", Integer.class, dialog.id()))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from friendships where user_low_id = ? and user_high_id = ?",
            Integer.class,
            lowUserId,
            highUserId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from user_blocks where blocker_user_id = ? or blocked_user_id = ?",
            Integer.class,
            captainId,
            captainId
        )).isZero();
    }

    private UUID createUser(String email, String username) {
        return userPersistencePort.create(new NewUserRecord(
            UUID.randomUUID(),
            email,
            username,
            username,
            "hash",
            NOW
        )).id();
    }

    private static int compareUuid(UUID firstUserId, UUID secondUserId) {
        int highBitsComparison = Long.compareUnsigned(firstUserId.getMostSignificantBits(), secondUserId.getMostSignificantBits());
        return highBitsComparison != 0
            ? highBitsComparison
            : Long.compareUnsigned(firstUserId.getLeastSignificantBits(), secondUserId.getLeastSignificantBits());
    }
}
