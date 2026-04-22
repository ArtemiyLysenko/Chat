package edu.artemiy.chat.adapters.xmpp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.artemiy.chat.contacts.api.ContactsException;
import edu.artemiy.chat.contacts.api.ContactsService;
import edu.artemiy.chat.contacts.api.DirectDialogSummary;
import edu.artemiy.chat.contacts.api.DirectMessageEligibility;
import edu.artemiy.chat.federation.api.FederationTelemetry;
import edu.artemiy.chat.federation.api.JabberConnectionStatus;
import edu.artemiy.chat.identity.api.ResolvedUser;
import edu.artemiy.chat.identity.api.UserDirectoryQuery;
import edu.artemiy.chat.identity.api.UsernamePasswordAuthenticationCommand;
import edu.artemiy.chat.messaging.api.ChatTargetRef;
import edu.artemiy.chat.messaging.api.ChatTargetType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.messaging.api.MessagingService;
import edu.artemiy.chat.messaging.api.SendMessageCommand;

final class XmppConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(XmppConnectionHandler.class);
    private static final String STREAM_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";
    private static final String BIND_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-bind";
    private static final String FEDERATION_NAMESPACE = "urn:chat:federation:1";

    private final Socket socket;
    private final XmppProperties properties;
    private final FederationTransportProperties federationProperties;
    private final UserDirectoryQuery userDirectoryQuery;
    private final ContactsService contactsService;
    private final MessagingService messagingService;
    private final XmppSessionRegistry sessionRegistry;
    private final FederationTelemetry federationTelemetry;
    private final XmppFederationGateway federationGateway;
    private final XMLInputFactory inputFactory = XMLInputFactory.newFactory();
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private BufferedWriter writer;
    private ResolvedUser authenticatedUser;
    private XmppConnectionSession session;
    private String clientConnectionId;
    private String authenticatedPeerDomain;
    private JabberConnectionStatus closingStatus;

    XmppConnectionHandler(
        Socket socket,
        XmppProperties properties,
        FederationTransportProperties federationProperties,
        UserDirectoryQuery userDirectoryQuery,
        ContactsService contactsService,
        MessagingService messagingService,
        XmppSessionRegistry sessionRegistry,
        FederationTelemetry federationTelemetry,
        XmppFederationGateway federationGateway
    ) {
        this.socket = socket;
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
    public void run() {
        try (socket) {
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            readStream(socket.getInputStream());
        }
        catch (IOException | XMLStreamException exception) {
            if (!closed.get()) {
                log.debug("XMPP connection closed", exception);
            }
            if (clientConnectionId != null && closingStatus == null) {
                closingStatus = JabberConnectionStatus.ERROR;
            }
        }
        finally {
            sessionRegistry.remove(session);
            if (clientConnectionId != null) {
                federationTelemetry.recordXmppClientClosed(
                    clientConnectionId,
                    closingStatus == null ? JabberConnectionStatus.DISCONNECTED : closingStatus
                );
            }
            closed.set(true);
        }
    }

    void sendMessage(String fromJid, String toJid, UUID stanzaId, String bodyText) {
        sendRaw(XmppXml.message(fromJid, toJid, stanzaId, bodyText));
    }

    void sendPresence(String fromJid, String toJid, String type) {
        sendRaw(XmppXml.presence(fromJid, toJid, type));
    }

    void closeSilently() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close();
        }
        catch (IOException ignored) {
        }
    }

    private void readStream(InputStream inputStream) throws XMLStreamException, IOException {
        XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream, StandardCharsets.UTF_8.name());
        while (!closed.get() && reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                handleStartElement(reader);
                continue;
            }
            if (eventType == XMLStreamConstants.END_ELEMENT
                && STREAM_NAMESPACE.equals(reader.getNamespaceURI())
                && "stream".equals(reader.getLocalName())) {
                return;
            }
        }
    }

    private void handleStartElement(XMLStreamReader reader) throws XMLStreamException, IOException {
        String namespace = reader.getNamespaceURI();
        String localName = reader.getLocalName();
        if (STREAM_NAMESPACE.equals(namespace) && "stream".equals(localName)) {
            handleStreamOpen(reader);
            return;
        }
        if (SASL_NAMESPACE.equals(namespace) && "auth".equals(localName)) {
            handleAuthentication(reader);
            return;
        }
        if (FEDERATION_NAMESPACE.equals(namespace) && "auth".equals(localName)) {
            handleFederationAuthentication(reader);
            return;
        }
        if ("iq".equals(localName)) {
            handleIq(reader);
            return;
        }
        if ("presence".equals(localName)) {
            handlePresence(reader);
            return;
        }
        if ("message".equals(localName)) {
            handleMessage(reader);
        }
    }

    private void handleStreamOpen(XMLStreamReader reader) throws IOException {
        XmppAddress destination = XmppAddress.parse(attribute(reader, "to"));
        if (!properties.getDomain().equalsIgnoreCase(destination.domain())) {
            sendRaw(XmppXml.hostUnknownError());
            sendRaw(XmppXml.streamClose());
            closeSilently();
            return;
        }
        sendRaw(XmppXml.streamOpen(properties.getDomain(), UUID.randomUUID().toString()));
        sendRaw(authenticatedUser == null && authenticatedPeerDomain == null
            ? XmppXml.preAuthenticationFeatures()
            : XmppXml.postAuthenticationFeatures());
    }

    private void handleAuthentication(XMLStreamReader reader) throws XMLStreamException, IOException {
        if (clientConnectionId == null) {
            clientConnectionId = UUID.randomUUID().toString();
            federationTelemetry.recordXmppClientAuthenticating(clientConnectionId, remoteAddress());
        }
        if (!"PLAIN".equals(attribute(reader, "mechanism"))) {
            closingStatus = JabberConnectionStatus.ERROR;
            sendRaw(XmppXml.authenticationFailure());
            sendRaw(XmppXml.streamClose());
            closeSilently();
            return;
        }
        try {
            String payload = reader.getElementText();
            String[] fields = new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8).split("\u0000", -1);
            String username = fields.length >= 2 ? fields[1] : "";
            String password = fields.length >= 3 ? fields[2] : "";
            authenticatedUser = userDirectoryQuery.authenticateByUsernamePassword(
                new UsernamePasswordAuthenticationCommand(username, password)
            );
        }
        catch (RuntimeException exception) {
            closingStatus = JabberConnectionStatus.ERROR;
            sendRaw(XmppXml.authenticationFailure());
            sendRaw(XmppXml.streamClose());
            closeSilently();
            return;
        }
        sendRaw(XmppXml.authenticationSuccess());
    }

    private void handleFederationAuthentication(XMLStreamReader reader) throws XMLStreamException, IOException {
        String fromDomain = normalize(attribute(reader, "from"));
        String sharedSecret = attribute(reader, "secret");
        drainCurrentElement(reader, "auth");
        if (!federationProperties.isEnabled()
            || !federationProperties.matchesPeerDomain(fromDomain)
            || !federationProperties.hasSharedSecret()
            || !federationProperties.getSharedSecret().equals(sharedSecret)) {
            sendRaw(XmppXml.federationAuthFailure());
            sendRaw(XmppXml.streamClose());
            closeSilently();
            return;
        }
        authenticatedPeerDomain = fromDomain;
        sendRaw(XmppXml.federationAuthSuccess());
    }

    private void handleIq(XMLStreamReader reader) throws XMLStreamException, IOException {
        String stanzaId = attribute(reader, "id");
        String type = attribute(reader, "type");
        String resource = null;
        boolean bindRequest = false;

        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT
                && BIND_NAMESPACE.equals(reader.getNamespaceURI())
                && "bind".equals(reader.getLocalName())) {
                bindRequest = true;
                resource = readBindResource(reader);
                continue;
            }
            if (eventType == XMLStreamConstants.END_ELEMENT && "iq".equals(reader.getLocalName())) {
                break;
            }
        }

        if (!bindRequest || authenticatedUser == null || !"set".equals(type)) {
            sendRaw(XmppXml.iqError(stanzaId, "cancel", "service-unavailable"));
            return;
        }

        String assignedResource = resource == null || resource.isBlank() ? "xmpp-" + UUID.randomUUID() : resource.trim();
        XmppAddress address = XmppAddress.parse(authenticatedUser.username() + "@" + properties.getDomain() + "/" + assignedResource);
        session = new XmppConnectionSession(
            authenticatedUser.userId(),
            authenticatedUser.username(),
            authenticatedUser.displayName(),
            assignedResource,
            address.bareJid(),
            address.fullJid(assignedResource),
            Instant.now(),
            String.valueOf(socket.getRemoteSocketAddress()),
            this,
            new AtomicBoolean(false)
        );
        sessionRegistry.register(session);
        federationTelemetry.recordXmppClientConnected(
            clientConnectionId,
            authenticatedUser.userId(),
            session.fullJid(),
            session.resource(),
            remoteAddress()
        );
        sendRaw(XmppXml.bindResult(stanzaId, session.fullJid()));
    }

    private void handlePresence(XMLStreamReader reader) throws XMLStreamException {
        String type = attribute(reader, "type");
        drainCurrentElement(reader, "presence");
        if (session == null) {
            return;
        }
        if ("unavailable".equals(type)) {
            sessionRegistry.markUnavailable(session);
            return;
        }
        sessionRegistry.markAvailable(session);
    }

    private void handleMessage(XMLStreamReader reader) throws XMLStreamException, IOException {
        String from = attribute(reader, "from");
        String to = attribute(reader, "to");
        String stanzaId = attribute(reader, "id");
        String bodyText = null;
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT && "body".equals(reader.getLocalName())) {
                bodyText = reader.getElementText();
                continue;
            }
            if (eventType == XMLStreamConstants.END_ELEMENT && "message".equals(reader.getLocalName())) {
                break;
            }
        }

        if (authenticatedPeerDomain != null) {
            handleFederatedMessage(from, to, stanzaId, bodyText);
            return;
        }
        if (session == null || bodyText == null || bodyText.isBlank()) {
            return;
        }

        XmppAddress address;
        try {
            address = XmppAddress.parse(to);
        }
        catch (IllegalArgumentException exception) {
            sendRaw(XmppXml.messageError(session.fullJid(), to == null ? session.bareJid() : to, stanzaId, "jid-malformed"));
            return;
        }
        if (!properties.getDomain().equalsIgnoreCase(address.domain())) {
            handleOutboundFederatedMessage(address, stanzaId, bodyText.trim());
            return;
        }

        ResolvedUser recipient = address.localpart() == null ? null : userDirectoryQuery.findActiveUserByUsername(address.localpart()).orElse(null);
        if (recipient == null) {
            sendRaw(XmppXml.messageError(session.fullJid(), address.bareJid(), stanzaId, "item-not-found"));
            return;
        }

        try {
            DirectDialogSummary dialog = contactsService.ensureDirectDialog(session.userId(), recipient.userId());
            messagingService.sendMessage(
                session.userId(),
                new SendMessageCommand(new ChatTargetRef(ChatTargetType.DIRECT, dialog.dialogId()), bodyText.trim())
            );
        }
        catch (ContactsException | MessagingException exception) {
            sendRaw(XmppXml.messageError(session.fullJid(), recipient.username() + "@" + properties.getDomain(), stanzaId, "not-allowed"));
        }
    }

    private void handleOutboundFederatedMessage(XmppAddress recipientAddress, String stanzaId, String bodyText) {
        if (!federationProperties.isEnabled() || !federationProperties.matchesPeerDomain(recipientAddress.domain())) {
            sendRaw(XmppXml.messageError(session.fullJid(), recipientAddress.bareJid(), stanzaId, "remote-server-not-found"));
            return;
        }
        ResolvedUser mirroredRecipient = recipientAddress.localpart() == null
            ? null
            : userDirectoryQuery.findActiveUserByUsername(recipientAddress.localpart()).orElse(null);
        if (mirroredRecipient == null) {
            sendRaw(XmppXml.messageError(session.fullJid(), recipientAddress.bareJid(), stanzaId, "item-not-found"));
            federationTelemetry.recordFederationError(federationProperties.normalizedPeerDomain(), federationProperties.configJson());
            return;
        }
        try {
            if (contactsService.evaluateDirectMessageEligibility(session.userId(), mirroredRecipient.userId())
                != DirectMessageEligibility.ELIGIBLE) {
                sendRaw(XmppXml.messageError(session.fullJid(), recipientAddress.bareJid(), stanzaId, "not-allowed"));
                return;
            }
        }
        catch (ContactsException exception) {
            sendRaw(XmppXml.messageError(session.fullJid(), recipientAddress.bareJid(), stanzaId, "not-allowed"));
            federationTelemetry.recordFederationError(federationProperties.normalizedPeerDomain(), federationProperties.configJson());
            return;
        }

        try {
            log.debug("Forwarding federated direct message {} -> {}", session.bareJid(), recipientAddress.bareJid());
            federationGateway.forwardDirectMessage(session.bareJid(), recipientAddress.bareJid(), bodyText);
            federationTelemetry.recordFederationOutboundMessage(
                federationProperties.normalizedPeerDomain(),
                federationProperties.configJson()
            );
        }
        catch (XmppFederationGateway.FederationDeliveryException | IOException | XMLStreamException exception) {
            log.debug(
                "Federated delivery {} -> {} failed: {}",
                session.bareJid(),
                recipientAddress.bareJid(),
                exception.getMessage(),
                exception
            );
            federationTelemetry.recordFederationError(
                federationProperties.normalizedPeerDomain(),
                federationProperties.configJson()
            );
            sendRaw(XmppXml.messageError(session.fullJid(), recipientAddress.bareJid(), stanzaId, "service-unavailable"));
        }
    }

    private void handleFederatedMessage(String from, String to, String stanzaId, String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            federationTelemetry.recordFederationError(authenticatedPeerDomain, federationProperties.configJson());
            sendRaw(XmppXml.federationDeliveryError(stanzaId, "bad-request"));
            return;
        }
        XmppAddress senderAddress;
        XmppAddress recipientAddress;
        try {
            senderAddress = XmppAddress.parse(from);
            recipientAddress = XmppAddress.parse(to);
        }
        catch (IllegalArgumentException exception) {
            federationTelemetry.recordFederationError(authenticatedPeerDomain, federationProperties.configJson());
            sendRaw(XmppXml.federationDeliveryError(stanzaId, "jid-malformed"));
            return;
        }
        if (senderAddress.localpart() == null
            || recipientAddress.localpart() == null
            || !normalize(senderAddress.domain()).equals(authenticatedPeerDomain)
            || !properties.getDomain().equalsIgnoreCase(recipientAddress.domain())) {
            federationTelemetry.recordFederationError(authenticatedPeerDomain, federationProperties.configJson());
            sendRaw(XmppXml.federationDeliveryError(stanzaId, "forbidden"));
            return;
        }

        ResolvedUser mirroredSender = userDirectoryQuery.findActiveUserByUsername(senderAddress.localpart()).orElse(null);
        ResolvedUser recipient = userDirectoryQuery.findActiveUserByUsername(recipientAddress.localpart()).orElse(null);
        if (mirroredSender == null || recipient == null) {
            federationTelemetry.recordFederationError(authenticatedPeerDomain, federationProperties.configJson());
            sendRaw(XmppXml.federationDeliveryError(stanzaId, "item-not-found"));
            return;
        }

        try {
            log.debug("Persisting inbound federated message {} -> {}", senderAddress.bareJid(), recipientAddress.bareJid());
            DirectDialogSummary dialog = contactsService.ensureDirectDialog(mirroredSender.userId(), recipient.userId());
            messagingService.sendMessage(
                mirroredSender.userId(),
                new SendMessageCommand(new ChatTargetRef(ChatTargetType.DIRECT, dialog.dialogId()), bodyText.trim())
            );
            federationTelemetry.recordFederationInboundMessage(authenticatedPeerDomain, federationProperties.configJson());
            log.debug("Acknowledging inbound federated stanza {} from {}", stanzaId, authenticatedPeerDomain);
            sendRaw(XmppXml.federationAck(stanzaId));
        }
        catch (ContactsException | MessagingException exception) {
            federationTelemetry.recordFederationError(authenticatedPeerDomain, federationProperties.configJson());
            sendRaw(XmppXml.federationDeliveryError(stanzaId, "not-allowed"));
        }
    }

    private String readBindResource(XMLStreamReader reader) throws XMLStreamException {
        String resource = null;
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT && "resource".equals(reader.getLocalName())) {
                resource = reader.getElementText();
                continue;
            }
            if (eventType == XMLStreamConstants.END_ELEMENT && "bind".equals(reader.getLocalName())) {
                break;
            }
        }
        return resource;
    }

    private void drainCurrentElement(XMLStreamReader reader, String elementName) throws XMLStreamException {
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.END_ELEMENT && elementName.equals(reader.getLocalName())) {
                return;
            }
        }
    }

    private String attribute(XMLStreamReader reader, String attributeName) {
        String value = reader.getAttributeValue(null, attributeName);
        return value == null ? "" : value;
    }

    private void sendRaw(String xml) {
        if (closed.get()) {
            return;
        }
        synchronized (writeLock) {
            try {
                writer.write(xml);
                writer.flush();
            }
            catch (IOException exception) {
                closeSilently();
            }
        }
    }

    private String remoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
