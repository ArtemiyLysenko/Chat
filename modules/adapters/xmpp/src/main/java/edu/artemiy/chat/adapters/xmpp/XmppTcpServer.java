package edu.artemiy.chat.adapters.xmpp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.federation.api.FederationTelemetry;
import edu.artemiy.chat.identity.api.UserDirectoryQuery;
import edu.artemiy.chat.messaging.api.MessagingService;

@Component
public final class XmppTcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(XmppTcpServer.class);

    private final XmppProperties properties;
    private final FederationTransportProperties federationProperties;
    private final UserDirectoryQuery userDirectoryQuery;
    private final ContactsService contactsService;
    private final MessagingService messagingService;
    private final XmppSessionRegistry sessionRegistry;
    private final FederationTelemetry federationTelemetry;
    private final XmppFederationGateway federationGateway;
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "chat-xmpp-accept");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "chat-xmpp-client");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;

    XmppTcpServer(
        XmppProperties properties,
        FederationTransportProperties federationProperties,
        UserDirectoryQuery userDirectoryQuery,
        ContactsService contactsService,
        MessagingService messagingService,
        XmppSessionRegistry sessionRegistry,
        FederationTelemetry federationTelemetry,
        XmppFederationGateway federationGateway
    ) {
        this.properties = properties;
        this.federationProperties = federationProperties;
        this.userDirectoryQuery = userDirectoryQuery;
        this.contactsService = contactsService;
        this.messagingService = messagingService;
        this.sessionRegistry = sessionRegistry;
        this.federationTelemetry = federationTelemetry;
        this.federationGateway = federationGateway;
    }

    @Override
    public void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        try {
            ServerSocket createdServerSocket = new ServerSocket();
            createdServerSocket.setReuseAddress(true);
            createdServerSocket.bind(new InetSocketAddress(properties.getPort()));
            serverSocket = createdServerSocket;
            running = true;
            acceptExecutor.execute(this::acceptLoop);
            log.info("XMPP adapter listening on {}", localPort());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to start XMPP adapter on port " + properties.getPort(), exception);
        }
    }

    @Override
    public void stop() {
        running = false;
        closeServerSocket();
        sessionRegistry.disconnectAll();
    }

    @PreDestroy
    void shutdown() {
        stop();
        acceptExecutor.shutdownNow();
        connectionExecutor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    public int localPort() {
        return serverSocket == null ? properties.getPort() : serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                connectionExecutor.execute(new XmppConnectionHandler(
                    socket,
                    properties,
                    federationProperties,
                    userDirectoryQuery,
                    contactsService,
                    messagingService,
                    sessionRegistry
                    ,
                    federationTelemetry,
                    federationGateway
                ));
            }
            catch (IOException exception) {
                if (running) {
                    log.warn("XMPP accept loop stopped unexpectedly", exception);
                }
                return;
            }
        }
    }

    private void closeServerSocket() {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        }
        catch (IOException ignored) {
        }
        finally {
            serverSocket = null;
        }
    }
}
