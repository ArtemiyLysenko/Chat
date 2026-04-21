package edu.artemiy.chat.adapters.persistence.jpa.attachments;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.attachments.spi.AttachmentPersistencePort;
import edu.artemiy.chat.attachments.spi.NewAttachmentRecord;
import edu.artemiy.chat.attachments.spi.NewMessageAttachmentRecord;
import edu.artemiy.chat.contacts.spi.ContactsPersistencePort;
import edu.artemiy.chat.contacts.spi.NewDirectDialogRecord;
import edu.artemiy.chat.identity.spi.NewUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessageState;
import edu.artemiy.chat.messaging.spi.MessagingPersistencePort;
import edu.artemiy.chat.messaging.spi.NewMessageRecord;
import edu.artemiy.chat.messaging.spi.StoredMessageAttachment;
import edu.artemiy.chat.rooms.api.MembershipRole;
import edu.artemiy.chat.rooms.api.RoomVisibility;
import edu.artemiy.chat.rooms.spi.NewRoomMembershipRecord;
import edu.artemiy.chat.rooms.spi.NewRoomRecord;
import edu.artemiy.chat.rooms.spi.RoomPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class AttachmentsRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-21T18:00:00Z");

    @Autowired
    private AttachmentPersistencePort attachmentPersistencePort;

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
                    message_attachments,
                    attachments,
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
    void persistsAttachmentMetadataAndLoadsAttachmentsWithMessageHistory() {
        UUID authorId = createUser("captain@example.com", "captain");
        UUID roomId = createRoom(authorId, "Blueprints");
        ChatTargetRef roomChat = new ChatTargetRef(ChatTargetType.ROOM, roomId);
        UUID messageId = UUID.fromString("81000000-0000-0000-0000-000000000001");

        messagingPersistencePort.createMessage(new NewMessageRecord(
            messageId,
            roomChat,
            authorId,
            null,
            "Attached files",
            MessageState.ACTIVE,
            NOW
        ));

        UUID secondAttachmentId = UUID.fromString("81000000-0000-0000-0000-000000000002");
        UUID firstAttachmentId = UUID.fromString("81000000-0000-0000-0000-000000000003");
        attachmentPersistencePort.createAttachment(new NewAttachmentRecord(
            secondAttachmentId,
            "uploads/" + secondAttachmentId,
            "notes.txt",
            "text/plain",
            42,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            authorId,
            roomChat,
            NOW
        ));
        attachmentPersistencePort.createAttachment(new NewAttachmentRecord(
            firstAttachmentId,
            "uploads/" + firstAttachmentId,
            "diagram.png",
            "image/png",
            2048,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            authorId,
            roomChat,
            NOW.plusSeconds(1)
        ));
        attachmentPersistencePort.attachToMessage(new NewMessageAttachmentRecord(messageId, secondAttachmentId, "Later item", 1));
        attachmentPersistencePort.attachToMessage(new NewMessageAttachmentRecord(messageId, firstAttachmentId, "Primary image", 0));

        assertThat(attachmentPersistencePort.findAttachment(firstAttachmentId))
            .hasValueSatisfying(attachment -> {
                assertThat(attachment.chat()).isEqualTo(roomChat);
                assertThat(attachment.storageKey()).isEqualTo("uploads/" + firstAttachmentId);
            });
        assertThat(messagingPersistencePort.findMessage(messageId))
            .hasValueSatisfying(message -> assertThat(message.attachments())
                .extracting(attachment -> attachment.attachmentId().toString(), StoredMessageAttachment::commentText)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(firstAttachmentId.toString(), "Primary image"),
                    org.assertj.core.groups.Tuple.tuple(secondAttachmentId.toString(), "Later item")
                ));
        assertThat(messagingPersistencePort.listLatestMessages(roomChat, 10))
            .singleElement()
            .satisfies(message -> assertThat(message.attachments())
                .extracting(StoredMessageAttachment::sortOrder)
                .containsExactly(0, 1));
    }

    @Test
    void enforcesAttachmentTargetExclusivity() {
        UUID authorId = createUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "captain@example.com", "captain");
        UUID roomId = createRoom(authorId, "Bridge");
        UUID directDialogId = createDirectDialog(
            authorId,
            createUser(UUID.fromString("00000000-0000-0000-0000-000000000002"), "scout@example.com", "scout")
        );

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into attachments (
                    id,
                    storage_key,
                    original_name,
                    media_type,
                    size_bytes,
                    sha256,
                    uploaded_by_user_id,
                    chat_target_type,
                    room_id,
                    direct_dialog_id,
                    created_at
                ) values (
                    '%s'::uuid,
                    'uploads/bad-both',
                    'bad.txt',
                    'text/plain',
                    10,
                    'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                    '%s'::uuid,
                    'ROOM',
                    '%s'::uuid,
                    '%s'::uuid,
                    '%s'::timestamptz
                )
                """.formatted(UUID.randomUUID(), authorId, roomId, directDialogId, NOW)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.execute(
            """
                insert into attachments (
                    id,
                    storage_key,
                    original_name,
                    media_type,
                    size_bytes,
                    sha256,
                    uploaded_by_user_id,
                    chat_target_type,
                    room_id,
                    direct_dialog_id,
                    created_at
                ) values (
                    '%s'::uuid,
                    'uploads/bad-none',
                    'bad.txt',
                    'text/plain',
                    10,
                    'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
                    '%s'::uuid,
                    'ROOM',
                    null,
                    null,
                    '%s'::timestamptz
                )
                """.formatted(UUID.randomUUID(), authorId, NOW.plusSeconds(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID createUser(String email, String username) {
        return createUser(UUID.randomUUID(), email, username);
    }

    private UUID createUser(UUID userId, String email, String username) {
        return userPersistencePort.create(new NewUserRecord(
            userId,
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
        UUID lowUserId = firstUserId.compareTo(secondUserId) <= 0 ? firstUserId : secondUserId;
        UUID highUserId = lowUserId.equals(firstUserId) ? secondUserId : firstUserId;
        return contactsPersistencePort.createDirectDialog(new NewDirectDialogRecord(
            UUID.randomUUID(),
            lowUserId,
            highUserId,
            NOW
        )).id();
    }
}
