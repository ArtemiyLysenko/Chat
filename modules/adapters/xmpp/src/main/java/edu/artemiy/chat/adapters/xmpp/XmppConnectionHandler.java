package edu.artemiy.chat.adapters.xmpp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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

    private final Socket socket;
    private final XmppProperties properties;
    private final UserDirectoryQuery userDirectoryQuery;
    private final ContactsService contactsService;
    private final MessagingService messagingService;
    private final XmppSessionRegistry sessionRegistry;
    private final XMLInputFactory inputFactory = XMLInputFactory.newFactory();
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private BufferedWriter writer;
    private ResolvedUser authenticatedUser;
    private XmppConnectionSession session;

    XmppConnectionHandler(
        Socket socket,
        XmppProperties properties,
        UserDirectoryQuery userDirectoryQuery,
        ContactsService contactsService,
        MessagingService messagingService,
        XmppSessionRegistry sessionRegistry
    ) {
        this.socket = socket;
        this.properties = properties;
        this.userDirectoryQuery = userDirectoryQuery;
        this.contactsService = contactsService;
        this.messagingService = messagingService;
        this.sessionRegistry = sessionRegistry;
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
        }
        finally {
            sessionRegistry.remove(session);
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
        sendRaw(authenticatedUser == null ? XmppXml.preAuthenticationFeatures() : XmppXml.postAuthenticationFeatures());
    }

    private void handleAuthentication(XMLStreamReader reader) throws XMLStreamException, IOException {
        if (!"PLAIN".equals(attribute(reader, "mechanism"))) {
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
            sendRaw(XmppXml.authenticationFailure());
            sendRaw(XmppXml.streamClose());
            closeSilently();
            return;
        }
        sendRaw(XmppXml.authenticationSuccess());
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
            sendRaw(XmppXml.messageError(session.fullJid(), address.bareJid(), stanzaId, "remote-server-not-found"));
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
}
