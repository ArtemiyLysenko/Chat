package edu.artemiy.chat.adapters.xmpp;

import java.util.UUID;

final class XmppXml {

    private XmppXml() {
    }

    static String streamOpen(String domain, String streamId) {
        return """
            <?xml version='1.0' encoding='UTF-8'?><stream:stream from='%s' id='%s' version='1.0' xml:lang='en' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>
            """.formatted(escape(domain), escape(streamId));
    }

    static String federationStreamOpen(String fromDomain, String toDomain, String streamId) {
        return """
            <stream:stream xmlns='jabber:server' to='%s' xmlns:stream='http://etherx.jabber.org/streams' version='1.0' from='%s' xml:lang='en-US' id='%s'>
            """.formatted(escape(toDomain), escape(fromDomain), escape(streamId));
    }

    static String streamClose() {
        return "</stream:stream>";
    }

    static String preAuthenticationFeatures() {
        return """
            <stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms><federation xmlns='urn:chat:federation:1'/></stream:features>
            """;
    }

    static String postAuthenticationFeatures() {
        return """
            <stream:features><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/></stream:features>
            """;
    }

    static String authenticationSuccess() {
        return "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";
    }

    static String authenticationFailure() {
        return "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><not-authorized/></failure>";
    }

    static String federationAuthRequest(String fromDomain, String sharedSecret) {
        return """
            <auth xmlns='urn:chat:federation:1' from='%s' secret='%s'/>
            """.formatted(escape(fromDomain), escape(sharedSecret));
    }

    static String federationAuthSuccess() {
        return "<success xmlns='urn:chat:federation:1'/>";
    }

    static String federationAuthFailure() {
        return "<failure xmlns='urn:chat:federation:1'><not-authorized/></failure>";
    }

    static String federationAck(String stanzaId) {
        return """
            <ack xmlns='urn:chat:federation:1' id='%s'/>
            """.formatted(escape(stanzaId == null ? "" : stanzaId));
    }

    static String federationDeliveryError(String stanzaId, String condition) {
        return """
            <error xmlns='urn:chat:federation:1' id='%s' condition='%s'/>
            """.formatted(escape(stanzaId == null ? "" : stanzaId), escape(condition));
    }

    static String hostUnknownError() {
        return """
            <stream:error><host-unknown xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>
            """;
    }

    static String bindResult(String stanzaId, String fullJid) {
        return """
            <iq type='result' id='%s'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>%s</jid></bind></iq>
            """.formatted(escape(stanzaId), escape(fullJid));
    }

    static String iqError(String stanzaId, String type, String condition) {
        return """
            <iq type='error' id='%s'><error type='%s'><%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></error></iq>
            """.formatted(escape(stanzaId), escape(type), condition);
    }

    static String message(String from, String to, UUID stanzaId, String bodyText) {
        return """
            <message from='%s' to='%s' type='chat' id='%s'><body>%s</body></message>
            """.formatted(escape(from), escape(to), stanzaId, escape(bodyText));
    }

    static String messageError(String from, String to, String stanzaId, String condition) {
        return """
            <message from='%s' to='%s' type='error' id='%s'><error type='cancel'><%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></error></message>
            """.formatted(escape(from), escape(to), escape(stanzaId == null ? "" : stanzaId), condition);
    }

    static String presence(String from, String to, String type) {
        String typeAttribute = type == null ? "" : " type='%s'".formatted(escape(type));
        return "<presence from='%s' to='%s'%s/>".formatted(escape(from), escape(to), typeAttribute);
    }

    private static String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
