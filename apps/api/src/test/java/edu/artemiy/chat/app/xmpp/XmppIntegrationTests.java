package edu.artemiy.chat.app.xmpp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smack.XMPPException;
import org.jxmpp.jid.impl.JidCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.xmpp.XmppTcpServer;
import edu.artemiy.chat.app.bootstrap.ChatApplication;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.identity.api.IdentityService;
import edu.artemiy.chat.identity.api.RegisterUserCommand;
import edu.artemiy.chat.identity.spi.TombstoneUserRecord;
import edu.artemiy.chat.identity.spi.UserPersistencePort;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.messaging.api.SendMessageCommand;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(
    classes = ChatApplication.class,
    properties = {
        "chat.xmpp.enabled=true",
        "chat.xmpp.port=0",
        "chat.xmpp.domain=test.chat"
    }
)
@ActiveProfiles("test")
class XmppIntegrationTests extends PostgresIntegrationSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private ContactsService contactsService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private XmppTcpServer xmppTcpServer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @BeforeAll
    static void configureSmack() {
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN");
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
                    message_attachments,
                    attachments,
                    direct_dialogs,
                    user_blocks,
                    friendships,
                    friendship_requests,
                    moderation_audit_events,
                    room_bans,
                    room_invites,
                    room_memberships,
                    rooms,
                    session_tabs,
                    user_sessions,
                    password_reset_tokens,
                    users
                cascade
                """
        );
    }

    @Test
    void xmppClientsCanAuthenticateExchangePresenceAndShareDirectMessagesWithTheCoreMessagingFlow() throws Exception {
        TestUser captain = registerUser("captain");
        TestUser scout = registerUser("scout");
        befriend(captain, scout);

        XMPPTCPConnection captainConnection = connect("captain", "password123", "bridge-captain");
        XMPPTCPConnection scoutConnection = connect("scout", "password123", "bridge-scout");
        try {
            StanzaQueue captainPresenceQueue = new StanzaQueue();
            StanzaQueue scoutPresenceQueue = new StanzaQueue();
            StanzaQueue scoutMessageQueue = new StanzaQueue();
            captainConnection.addAsyncStanzaListener(captainPresenceQueue, stanza -> stanza instanceof Presence);
            scoutConnection.addAsyncStanzaListener(scoutPresenceQueue, stanza -> stanza instanceof Presence);
            scoutConnection.addAsyncStanzaListener(scoutMessageQueue, stanza -> stanza instanceof Message message && message.getBody() != null);

            captainConnection.sendStanza(new Presence(Presence.Type.available));
            scoutConnection.sendStanza(new Presence(Presence.Type.available));

            Presence captainSawScout = captainPresenceQueue.await(Presence.class);
            Presence scoutSawCaptain = scoutPresenceQueue.await(Presence.class);
            assertThat(captainSawScout.getFrom().toString()).startsWith("scout@test.chat/");
            assertThat(scoutSawCaptain.getFrom().toString()).startsWith("captain@test.chat/");

            Message xmppMessage = new Message(JidCreate.entityBareFrom("scout@test.chat"), Message.Type.chat);
            xmppMessage.setBody("Bridge ready");
            captainConnection.sendStanza(xmppMessage);

            Message deliveredXmppMessage = scoutMessageQueue.await(Message.class);
            assertThat(deliveredXmppMessage.getBody()).isEqualTo("Bridge ready");
            assertThat(deliveredXmppMessage.getFrom().toString()).isEqualTo("captain@test.chat");

            DirectDialogSummary dialog = contactsService.ensureDirectDialog(captain.userId(), scout.userId());
            assertThat(messagingService.readMessageHistory(
                captain.userId(),
                new ReadMessageHistoryQuery(new ChatTargetRef(ChatTargetType.DIRECT, dialog.dialogId()), null, 50)
            ).items()).extracting(message -> message.bodyText()).contains("Bridge ready");

            messagingService.sendMessage(
                captain.userId(),
                new SendMessageCommand(new ChatTargetRef(ChatTargetType.DIRECT, dialog.dialogId()), "From HTTP-compatible flow")
            );

            Message deliveredServiceMessage = scoutMessageQueue.await(Message.class);
            assertThat(deliveredServiceMessage.getBody()).isEqualTo("From HTTP-compatible flow");
            assertThat(deliveredServiceMessage.getFrom().toString()).isEqualTo("captain@test.chat");
        }
        finally {
            disconnectQuietly(captainConnection);
            disconnectQuietly(scoutConnection);
        }
    }

    @Test
    void tombstonedUsersAreRejectedDuringXmppAuthentication() {
        TestUser deleted = registerUser("deleted");
        userPersistencePort.tombstone(new TombstoneUserRecord(
            deleted.userId(),
            "deleted-%s@tombstone.chat".formatted(deleted.userId()),
            "deleted-%s".formatted(deleted.userId()),
            "Deleted user",
            Instant.parse("2026-04-22T08:00:00Z")
        ));

        assertThatThrownBy(() -> connect("deleted", "password123", "denied"))
            .isInstanceOfAny(SmackException.SmackSaslException.class, XMPPException.class, XmlPullParserException.class);
    }

    private TestUser registerUser(String username) {
        var registeredUser = identityService.register(new RegisterUserCommand(
            "%s@example.com".formatted(username),
            username,
            "password123"
        ));
        return new TestUser(registeredUser.id(), registeredUser.username());
    }

    private void befriend(TestUser first, TestUser second) {
        var submission = contactsService.createFriendRequest(
            first.userId(),
            new CreateFriendRequestCommand(null, second.username(), null)
        );
        contactsService.acceptFriendRequest(second.userId(), submission.requestId());
    }

    private XMPPTCPConnection connect(String username, String password, String resource) throws Exception {
        XMPPTCPConnection connection = new XMPPTCPConnection(
            XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(JidCreate.domainBareFrom("test.chat"))
                .setHost("127.0.0.1")
                .setPort(xmppTcpServer.localPort())
                .setUsernameAndPassword(username, password)
                .setResource(resource)
                .setCompressionEnabled(false)
                .setSendPresence(false)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .build()
        );
        try {
            connection.connect().login();
            return connection;
        }
        catch (Exception exception) {
            disconnectQuietly(connection);
            throw exception;
        }
    }

    private void disconnectQuietly(XMPPTCPConnection connection) {
        try {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
        catch (Exception ignored) {
        }
    }

    private record TestUser(UUID userId, String username) {
    }

    private static final class StanzaQueue implements StanzaListener {

        private final LinkedBlockingQueue<Stanza> queue = new LinkedBlockingQueue<>();

        @Override
        public void processStanza(Stanza packet) {
            queue.offer(packet);
        }

        <T extends Stanza> T await(Class<T> type) throws InterruptedException {
            Stanza stanza = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(stanza).isInstanceOf(type);
            return type.cast(stanza);
        }
    }
}
