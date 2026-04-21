package edu.artemiy.chat.adapters.persistence.jpa.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.ModerationAction;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.spi.DuplicateRoomNameException;
import edu.artemiy.chat.rooms.spi.NewModerationAuditRecord;
import edu.artemiy.chat.rooms.spi.NewRoomMembershipRecord;
import edu.artemiy.chat.rooms.spi.NewRoomRecord;
import edu.artemiy.chat.rooms.spi.RoomPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class RoomsRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-21T12:00:00Z");

    @Autowired
    private RoomPersistencePort roomPersistencePort;

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomMembershipJpaRepository roomMembershipJpaRepository;

    @Autowired
    private RoomBanJpaRepository roomBanJpaRepository;

    @Autowired
    private ModerationAuditEventJpaRepository moderationAuditEventJpaRepository;

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
    void enforcesUniqueRoomNames() {
        UUID ownerId = createUser("owner@example.com", "owner");
        UUID firstRoomId = UUID.randomUUID();

        roomPersistencePort.createRoom(
            new NewRoomRecord(firstRoomId, ownerId, "Town Hall", null, RoomVisibility.PUBLIC, NOW),
            new NewRoomMembershipRecord(firstRoomId, ownerId, MembershipRole.OWNER, NOW)
        );

        UUID secondRoomId = UUID.randomUUID();
        assertThatThrownBy(() -> roomPersistencePort.createRoom(
            new NewRoomRecord(secondRoomId, ownerId, "town hall", null, RoomVisibility.PRIVATE, NOW),
            new NewRoomMembershipRecord(secondRoomId, ownerId, MembershipRole.OWNER, NOW)
        )).isInstanceOf(DuplicateRoomNameException.class);
    }

    @Test
    void enforcesMembershipUniqueness() {
        UUID ownerId = createUser("owner@example.com", "owner");
        UUID memberId = createUser("member@example.com", "member");
        UUID roomId = createRoom(ownerId, "Lobby");

        roomMembershipJpaRepository.saveAndFlush(new RoomMembershipEntity(roomId, memberId, MembershipRole.MEMBER, NOW));

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into room_memberships (room_id, user_id, role, joined_at)
                values ('%s'::uuid, '%s'::uuid, 'ADMIN', '%s'::timestamptz)
                """.formatted(roomId, memberId, NOW.plusSeconds(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesBanUniqueness() {
        UUID ownerId = createUser("owner@example.com", "owner");
        UUID targetId = createUser("target@example.com", "target");
        UUID roomId = createRoom(ownerId, "Lobby");

        roomBanJpaRepository.saveAndFlush(new RoomBanEntity(roomId, targetId, ownerId, "First", NOW));

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into room_bans (room_id, user_id, banned_by_user_id, reason, created_at)
                values ('%s'::uuid, '%s'::uuid, '%s'::uuid, 'Second', '%s'::timestamptz)
                """.formatted(roomId, targetId, ownerId, NOW.plusSeconds(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsModerationAuditRecords() {
        UUID ownerId = createUser("owner@example.com", "owner");
        UUID memberId = createUser("member@example.com", "member");
        UUID roomId = createRoom(ownerId, "Moderation");

        roomPersistencePort.recordModerationEvents(List.of(new NewModerationAuditRecord(
            roomId,
            ownerId,
            memberId,
            ModerationAction.MEMBER_BANNED,
            "Spam",
            "{\"source\":\"integration-test\"}",
            NOW
        )));

        assertThat(moderationAuditEventJpaRepository.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getRoomId()).isEqualTo(roomId);
                assertThat(event.getActorUserId()).isEqualTo(ownerId);
                assertThat(event.getTargetUserId()).isEqualTo(memberId);
                assertThat(event.getAction()).isEqualTo(ModerationAction.MEMBER_BANNED);
                assertThat(event.getReason()).isEqualTo("Spam");
                assertThat(event.getMetadataJson()).isEqualTo("{\"source\":\"integration-test\"}");
                assertThat(event.getCreatedAt()).isEqualTo(NOW);
            });
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

    private UUID createRoom(UUID ownerId, String name) {
        UUID roomId = UUID.randomUUID();
        roomJpaRepository.saveAndFlush(new RoomEntity(roomId, ownerId, name, null, RoomVisibility.PUBLIC, NOW));
        roomMembershipJpaRepository.saveAndFlush(new RoomMembershipEntity(roomId, ownerId, MembershipRole.OWNER, NOW));
        return roomId;
    }
}
