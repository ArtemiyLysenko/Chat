package edu.artemiy.chat.app.xmpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import edu.artemiy.chat.adapters.xmpp.XmppTcpServer;
import edu.artemiy.chat.admin.api.AdminObservabilityQuery;
import edu.artemiy.chat.app.bootstrap.ChatApplication;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.CreateFriendRequestCommand;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.identity.api.IdentityService;
import edu.artemiy.chat.identity.api.RegisterUserCommand;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.ReadMessageHistoryQuery;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

class XmppFederationIntegrationTests extends PostgresIntegrationSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @BeforeAll
    static void configureSmack() {
        SASLAuthentication.unBlacklistSASLMechanism("PLAIN");
    }

    @Test
    void twoNodesExchangeFederatedDirectMessagesAndPersistTelemetry() throws Exception {
        PostgresDatabase databaseA = createIsolatedDatabase("xmpp_federation_a");
        PostgresDatabase databaseB = createIsolatedDatabase("xmpp_federation_b");
        int httpPortA = freePort();
        int httpPortB = freePort();
        int xmppPortA = freePort();
        int xmppPortB = freePort();

        try (FederationNode nodeA = FederationNode.start(
            "node-a",
            "node-a.local",
            httpPortA,
            xmppPortA,
            "node-b.local",
            xmppPortB,
            databaseA
        ); FederationNode nodeB = FederationNode.start(
            "node-b",
            "node-b.local",
            httpPortB,
            xmppPortB,
            "node-a.local",
            xmppPortA,
            databaseB
        )) {
            TestUser captainOnA = nodeA.registerUser("captain");
            TestUser scoutOnA = nodeA.registerUser("scout");
            nodeA.befriend(captainOnA, scoutOnA);

            TestUser captainOnB = nodeB.registerUser("captain");
            TestUser scoutOnB = nodeB.registerUser("scout");
            nodeB.befriend(captainOnB, scoutOnB);

            XMPPTCPConnection captainConnection = nodeA.connect("captain", "bridge-a");
            XMPPTCPConnection scoutConnection = nodeB.connect("scout", "bridge-b");
            try {
                StanzaQueue inboundOnB = new StanzaQueue();
                StanzaQueue inboundOnA = new StanzaQueue();
                scoutConnection.addAsyncStanzaListener(inboundOnB, stanza -> stanza instanceof Message message && message.getBody() != null);
                captainConnection.addAsyncStanzaListener(inboundOnA, stanza -> stanza instanceof Message message && message.getBody() != null);

                Message toRemoteScout = new Message(JidCreate.entityBareFrom("scout@node-b.local"), Message.Type.chat);
                toRemoteScout.setBody("From node A");
                captainConnection.sendStanza(toRemoteScout);

                Message deliveredOnB = inboundOnB.await(Message.class);
                assertThat(deliveredOnB.getBody()).isEqualTo("From node A");
                assertThat(deliveredOnB.getFrom().toString()).isEqualTo("captain@node-b.local");

                DirectDialogSummary dialogOnB = nodeB.contactsService.ensureDirectDialog(captainOnB.userId(), scoutOnB.userId());
                assertThat(nodeB.messagingService.readMessageHistory(
                    scoutOnB.userId(),
                    new ReadMessageHistoryQuery(new ChatTargetRef(ChatTargetType.DIRECT, dialogOnB.dialogId()), null, 50)
                ).items()).extracting(message -> message.bodyText()).contains("From node A");

                Message toRemoteCaptain = new Message(JidCreate.entityBareFrom("captain@node-a.local"), Message.Type.chat);
                toRemoteCaptain.setBody("From node B");
                scoutConnection.sendStanza(toRemoteCaptain);

                Message deliveredOnA = inboundOnA.await(Message.class);
                assertThat(deliveredOnA.getBody()).isEqualTo("From node B");
                assertThat(deliveredOnA.getFrom().toString()).isEqualTo("scout@node-a.local");

                DirectDialogSummary dialogOnA = nodeA.contactsService.ensureDirectDialog(captainOnA.userId(), scoutOnA.userId());
                assertThat(nodeA.messagingService.readMessageHistory(
                    captainOnA.userId(),
                    new ReadMessageHistoryQuery(new ChatTargetRef(ChatTargetType.DIRECT, dialogOnA.dialogId()), null, 50)
                ).items()).extracting(message -> message.bodyText()).contains("From node B");

                assertThat(nodeA.adminObservability.jabberConnections())
                    .anySatisfy(snapshot -> assertThat(snapshot.principal()).isEqualTo("captain@node-a.local/bridge-a"));
                assertThat(nodeB.adminObservability.jabberConnections())
                    .anySatisfy(snapshot -> assertThat(snapshot.principal()).isEqualTo("scout@node-b.local/bridge-b"));

                assertThat(nodeA.adminObservability.federationPeers())
                    .singleElement()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.peerDomain()).isEqualTo("node-b.local");
                        assertThat(snapshot.status().name()).isEqualTo("UP");
                    });
                assertThat(nodeB.adminObservability.federationPeers())
                    .singleElement()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.peerDomain()).isEqualTo("node-a.local");
                        assertThat(snapshot.status().name()).isEqualTo("UP");
                    });

                assertThat(nodeA.adminObservability.federationTraffic())
                    .first()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.peerDomain()).isEqualTo("node-b.local");
                        assertThat(snapshot.inboundMessages()).isEqualTo(1);
                        assertThat(snapshot.outboundMessages()).isEqualTo(1);
                        assertThat(snapshot.errorCount()).isZero();
                    });
                assertThat(nodeB.adminObservability.federationTraffic())
                    .first()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.peerDomain()).isEqualTo("node-a.local");
                        assertThat(snapshot.inboundMessages()).isEqualTo(1);
                        assertThat(snapshot.outboundMessages()).isEqualTo(1);
                        assertThat(snapshot.errorCount()).isZero();
                    });
            }
            finally {
                disconnectQuietly(captainConnection);
                disconnectQuietly(scoutConnection);
            }
        }
    }

    private static int freePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to allocate a free port.", exception);
        }
    }

    private static void disconnectQuietly(XMPPTCPConnection connection) {
        try {
            if (connection != null && connection.isConnected()) {
                connection.disconnect();
            }
        }
        catch (Exception ignored) {
        }
    }

    private record TestUser(UUID userId, String username) {
    }

    private static final class FederationNode implements AutoCloseable {

        private final ConfigurableApplicationContext context;
        private final IdentityService identityService;
        private final ContactsService contactsService;
        private final MessagingService messagingService;
        private final AdminObservabilityQuery adminObservability;
        private final XmppTcpServer xmppTcpServer;
        private final String domain;

        private FederationNode(
            ConfigurableApplicationContext context,
            IdentityService identityService,
            ContactsService contactsService,
            MessagingService messagingService,
            AdminObservabilityQuery adminObservability,
            XmppTcpServer xmppTcpServer,
            String domain
        ) {
            this.context = context;
            this.identityService = identityService;
            this.contactsService = contactsService;
            this.messagingService = messagingService;
            this.adminObservability = adminObservability;
            this.xmppTcpServer = xmppTcpServer;
            this.domain = domain;
        }

        static FederationNode start(
            String nodeId,
            String domain,
            int httpPort,
            int xmppPort,
            String peerDomain,
            int peerPort,
            PostgresDatabase database
        ) {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(ChatApplication.class)
                .run(
                    "--spring.profiles.active=test",
                    "--server.port=" + httpPort,
                    "--chat.auth.secure-cookie=false",
                    "--chat.node-id=" + nodeId,
                    "--chat.xmpp.enabled=true",
                    "--chat.xmpp.domain=" + domain,
                    "--chat.xmpp.port=" + xmppPort,
                    "--chat.federation.enabled=true",
                    "--chat.federation.peer-domain=" + peerDomain,
                    "--chat.federation.peer-host=127.0.0.1",
                    "--chat.federation.peer-port=" + peerPort,
                    "--chat.federation.shared-secret=test-federation-secret",
                    "--spring.datasource.url=" + database.jdbcUrl(),
                    "--spring.datasource.username=" + database.username(),
                    "--spring.datasource.password=" + database.password()
                );
            return new FederationNode(
                context,
                context.getBean(IdentityService.class),
                context.getBean(ContactsService.class),
                context.getBean(MessagingService.class),
                context.getBean(AdminObservabilityQuery.class),
                context.getBean(XmppTcpServer.class),
                domain
            );
        }

        TestUser registerUser(String username) {
            var registeredUser = identityService.register(new RegisterUserCommand(
                "%s@example.com".formatted(username),
                username,
                "password123"
            ));
            return new TestUser(registeredUser.id(), registeredUser.username());
        }

        void befriend(TestUser first, TestUser second) {
            var submission = contactsService.createFriendRequest(
                first.userId(),
                new CreateFriendRequestCommand(null, second.username(), null)
            );
            contactsService.acceptFriendRequest(second.userId(), submission.requestId());
        }

        XMPPTCPConnection connect(String username, String resource) throws Exception {
            XMPPTCPConnection connection = new XMPPTCPConnection(
                XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(JidCreate.domainBareFrom(domain))
                    .setHost("127.0.0.1")
                    .setPort(xmppTcpServer.localPort())
                    .setUsernameAndPassword(username, "password123")
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

        @Override
        public void close() {
            context.close();
        }
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
