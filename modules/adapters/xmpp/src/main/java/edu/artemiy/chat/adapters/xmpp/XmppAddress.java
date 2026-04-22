package edu.artemiy.chat.adapters.xmpp;

record XmppAddress(String localpart, String domain, String resource) {

    static XmppAddress parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JID is required.");
        }
        String trimmed = value.trim();
        int slashIndex = trimmed.indexOf('/');
        String bare = slashIndex >= 0 ? trimmed.substring(0, slashIndex) : trimmed;
        String resource = slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : null;
        int atIndex = bare.indexOf('@');
        if (atIndex < 0) {
            return new XmppAddress(null, bare, blankToNull(resource));
        }
        String localpart = bare.substring(0, atIndex);
        String domain = bare.substring(atIndex + 1);
        if (localpart.isBlank() || domain.isBlank()) {
            throw new IllegalArgumentException("JID must include localpart and domain.");
        }
        return new XmppAddress(localpart, domain, blankToNull(resource));
    }

    String bareJid() {
        return localpart == null ? domain : localpart + "@" + domain;
    }

    String fullJid(String fallbackResource) {
        String effectiveResource = resource == null ? fallbackResource : resource;
        return effectiveResource == null ? bareJid() : bareJid() + "/" + effectiveResource;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
