package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.NewDirectDialogRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.spi.NewRoomMembershipRecord;
import edu.artemiy.chat.rooms.spi.NewRoomRecord;
import edu.artemiy.chat.rooms.spi.RoomPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class MessagingRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-21T16:00:00Z");

    @Autowired
    private MessagingPersistencePort messagingPersistencePort;

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private RoomPersistencePort roomPersistencePort;

    @Autowired
    private ContactsPersistencePort contactsPersistencePort;

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
                    chat_unread_markers,
                    messages,
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
    void enforcesMessageTargetExclusivity() {
        UUID authorId = createUser("captain@example.com", "captain");
        UUID roomId = createRoom(authorId, "Bridge");
        UUID directDialogId = createDirectDialog(authorId, createUser("scout@example.com", "scout"));

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into messages (id, room_id, direct_dialog_id, author_user_id, body_text, state, created_at)
                values ('%s'::uuid, '%s'::uuid, '%s'::uuid, '%s'::uuid, 'invalid', 'ACTIVE', '%s'::timestamptz)
                """.formatted(UUID.randomUUID(), roomId, directDialogId, authorId, NOW)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into messages (id, room_id, direct_dialog_id, author_user_id, body_text, state, created_at)
                values ('%s'::uuid, null, null, '%s'::uuid, 'invalid', 'ACTIVE', '%s'::timestamptz)
                """.formatted(UUID.randomUUID(), authorId, NOW.plusSeconds(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loadsRoomHistoryInNewestFirstOrderAndHasRoomHistoryIndex() {
        UUID authorId = createUser("captain@example.com", "captain");
        UUID roomId = createRoom(authorId, "Bridge");
        ChatTargetRef roomChat = new ChatTargetRef(ChatTargetType.ROOM, roomId);

        createMessage(roomChat, authorId, "first", NOW.minusSeconds(40), "61000000-0000-0000-0000-000000000001");
        createMessage(roomChat, authorId, "second", NOW.minusSeconds(30), "61000000-0000-0000-0000-000000000002");
        createMessage(roomChat, authorId, "third", NOW.minusSeconds(20), "61000000-0000-0000-0000-000000000003");
        createMessage(roomChat, authorId, "fourth", NOW.minusSeconds(10), "61000000-0000-0000-0000-000000000004");

        var messages = messagingPersistencePort.listLatestMessages(roomChat, 3);

        assertThat(messages).extracting(message -> message.bodyText()).containsExactly("fourth", "third", "second");
        assertThat(indexNames("messages")).contains("idx_messages_room_history");
        assertThat(explainUsingIndex(
            """
                select id
                from messages
                where room_id = ?
                order by created_at desc, id desc
                limit 3
                """,
            roomId
        )).contains("idx_messages_room_history");
    }

    @Test
    void loadsDirectDialogHistoryInNewestFirstOrderAndHasDirectHistoryIndex() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID scoutId = createUser("scout@example.com", "scout");
        UUID directDialogId = createDirectDialog(captainId, scoutId);
        ChatTargetRef directChat = new ChatTargetRef(ChatTargetType.DIRECT, directDialogId);

        createMessage(directChat, captainId, "first", NOW.minusSeconds(40), "62000000-0000-0000-0000-000000000001");
        createMessage(directChat, scoutId, "second", NOW.minusSeconds(30), "62000000-0000-0000-0000-000000000002");
        createMessage(directChat, captainId, "third", NOW.minusSeconds(20), "62000000-0000-0000-0000-000000000003");
        createMessage(directChat, scoutId, "fourth", NOW.minusSeconds(10), "62000000-0000-0000-0000-000000000004");

        var messages = messagingPersistencePort.listLatestMessages(directChat, 3);

        assertThat(messages).extracting(message -> message.bodyText()).containsExactly("fourth", "third", "second");
        assertThat(indexNames("messages")).contains("idx_messages_direct_dialog_history");
        assertThat(explainUsingIndex(
            """
                select id
                from messages
                where direct_dialog_id = ?
                order by created_at desc, id desc
                limit 3
                """,
            directDialogId
        )).contains("idx_messages_direct_dialog_history");
    }

    @Test
    void persistsAndUpdatesUnreadMarkers() {
        UUID captainId = createUser("captain@example.com", "captain");
        UUID roomId = createRoom(captainId, "Bridge");
        ChatTargetRef roomChat = new ChatTargetRef(ChatTargetType.ROOM, roomId);
        UUID firstMessageId = createMessage(roomChat, captainId, "first", NOW.minusSeconds(20), "63000000-0000-0000-0000-000000000001").id();
        UUID secondMessageId = createMessage(roomChat, captainId, "second", NOW.minusSeconds(10), "63000000-0000-0000-0000-000000000002").id();

        messagingPersistencePort.saveUnreadMarker(captainId, roomChat, firstMessageId, NOW.minusSeconds(5));
        messagingPersistencePort.saveUnreadMarker(captainId, roomChat, secondMessageId, NOW);
        messagingPersistencePort.saveUnreadMarker(captainId, roomChat, firstMessageId, NOW.plusSeconds(5));

        assertThat(messagingPersistencePort.findUnreadMarker(captainId, roomChat))
            .hasValueSatisfying(marker -> {
                assertThat(marker.lastReadMessageId()).isEqualTo(secondMessageId);
                assertThat(marker.updatedAt()).isEqualTo(NOW);
            });
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from chat_unread_markers where user_id = ? and room_id = ?",
            Integer.class,
            captainId,
            roomId
        )).isEqualTo(1);
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
        roomPersistencePort.createRoom(
            new NewRoomRecord(roomId, ownerId, name, null, RoomVisibility.PUBLIC, NOW),
            new NewRoomMembershipRecord(roomId, ownerId, MembershipRole.OWNER, NOW)
        );
        return roomId;
    }

    private UUID createDirectDialog(UUID firstUserId, UUID secondUserId) {
        UUID lowUserId = compareUuid(firstUserId, secondUserId) <= 0 ? firstUserId : secondUserId;
        UUID highUserId = lowUserId.equals(firstUserId) ? secondUserId : firstUserId;
        return contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW
        )).id();
    }

    private edu.artemiy.chat.messaging.spi.StoredMessage createMessage(
        ChatTargetRef chat,
        UUID authorUserId,
        String bodyText,
        Instant createdAt,
        String messageId
    ) {
        return messagingPersistencePort.createMessage(new NewMessageRecord(
            UUID.fromString(messageId),
            chat,
            authorUserId,
            bodyText,
            MessageState.ACTIVE,
            createdAt
        ));
    }

    private List<String> indexNames(String tableName) {
        return jdbcTemplate.queryForList(
            "select indexname from pg_indexes where schemaname = 'public' and tablename = ?",
            String.class,
            tableName
        );
    }

    private String explainUsingIndex(String sql, Object... args) {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (PreparedStatement disableSeqscan = connection.prepareStatement("set enable_seqscan = off")) {
                disableSeqscan.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("explain (costs off) " + sql)) {
                for (int index = 0; index < args.length; index++) {
                    statement.setObject(index + 1, args[index]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<String> lines = new ArrayList<>();
                    while (resultSet.next()) {
                        lines.add(resultSet.getString(1));
                    }
                    return String.join("\n", lines);
                }
            }
        });
    }

    private static int compareUuid(UUID first, UUID second) {
        int highBitsComparison = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        return highBitsComparison != 0
            ? highBitsComparison
            : Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }
}
