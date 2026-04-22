package edu.artemiy.chat.adapters.xmpp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class XmppFederationGateway {

    private static final String FEDERATION_NAMESPACE = "urn:chat:federation:1";
    private static final Logger log = LoggerFactory.getLogger(XmppFederationGateway.class);

    private final XmppProperties xmppProperties;
    private final FederationTransportProperties federationProperties;
    private final XMLInputFactory inputFactory = XMLInputFactory.newFactory();

    XmppFederationGateway(XmppProperties xmppProperties, FederationTransportProperties federationProperties) {
        this.xmppProperties = xmppProperties;
        this.federationProperties = federationProperties;
    }

    void forwardDirectMessage(String fromJid, String toJid, String bodyText)
        throws IOException, XMLStreamException, FederationDeliveryException {
        if (!federationProperties.isEnabled() || !federationProperties.hasPeerEndpoint() || !federationProperties.hasSharedSecret()) {
            throw new FederationDeliveryException("service-unavailable");
        }

        try (Socket socket = new Socket()) {
            log.debug("Opening federation connection to {}:{} for {} -> {}",
                federationProperties.getPeerHost(),
                federationProperties.getPeerPort(),
                fromJid,
                toJid
            );
            socket.connect(new InetSocketAddress(federationProperties.getPeerHost(), federationProperties.getPeerPort()), 5_000);
            socket.setSoTimeout(5_000);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(XmppXml.federationStreamOpen(
                    xmppProperties.getDomain(),
                    federationProperties.normalizedPeerDomain(),
                    UUID.randomUUID().toString()
                ));
                writer.write(XmppXml.federationAuthRequest(
                    xmppProperties.getDomain(),
                    federationProperties.getSharedSecret()
                ));
                writer.flush();

                XMLStreamReader reader = inputFactory.createXMLStreamReader(socket.getInputStream(), StandardCharsets.UTF_8.name());
                waitForFederationAuthentication(reader);
                log.debug("Federation authentication accepted by {}", federationProperties.normalizedPeerDomain());

                UUID messageId = UUID.randomUUID();
                writer.write(XmppXml.message(fromJid, toJid, messageId, bodyText));
                writer.flush();
                waitForAcknowledgement(reader, messageId.toString());
                log.debug("Federation peer {} acknowledged stanza {}", federationProperties.normalizedPeerDomain(), messageId);

                writer.write(XmppXml.streamClose());
                writer.flush();
            }
        }
    }

    private void waitForFederationAuthentication(XMLStreamReader reader) throws XMLStreamException, FederationDeliveryException {
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if (FEDERATION_NAMESPACE.equals(reader.getNamespaceURI()) && "success".equals(reader.getLocalName())) {
                return;
            }
            if (FEDERATION_NAMESPACE.equals(reader.getNamespaceURI()) && "failure".equals(reader.getLocalName())) {
                throw new FederationDeliveryException("not-authorized");
            }
        }
        throw new FederationDeliveryException("service-unavailable");
    }

    private void waitForAcknowledgement(XMLStreamReader reader, String stanzaId) throws XMLStreamException, FederationDeliveryException {
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType != XMLStreamConstants.START_ELEMENT || !FEDERATION_NAMESPACE.equals(reader.getNamespaceURI())) {
                continue;
            }
            if ("ack".equals(reader.getLocalName()) && stanzaId.equals(attribute(reader, "id"))) {
                return;
            }
            if ("error".equals(reader.getLocalName()) && stanzaId.equals(attribute(reader, "id"))) {
                throw new FederationDeliveryException(attribute(reader, "condition"));
            }
        }
        throw new FederationDeliveryException("service-unavailable");
    }

    private static String attribute(XMLStreamReader reader, String attributeName) {
        String value = reader.getAttributeValue(null, attributeName);
        return value == null ? "" : value;
    }

    static final class FederationDeliveryException extends Exception {

        FederationDeliveryException(String message) {
            super(message);
        }
    }
}
