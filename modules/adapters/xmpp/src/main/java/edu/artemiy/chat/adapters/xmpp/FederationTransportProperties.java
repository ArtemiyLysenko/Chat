package edu.artemiy.chat.adapters.xmpp;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.federation")
public class FederationTransportProperties {

    private boolean enabled;
    private String peerDomain = "";
    private String peerHost = "";
    private int peerPort = 5222;
    private String sharedSecret = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPeerDomain() {
        return peerDomain;
    }

    public void setPeerDomain(String peerDomain) {
        this.peerDomain = peerDomain;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public void setPeerHost(String peerHost) {
        this.peerHost = peerHost;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public void setPeerPort(int peerPort) {
        this.peerPort = peerPort;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    String normalizedPeerDomain() {
        return peerDomain == null ? "" : peerDomain.trim().toLowerCase(Locale.ROOT);
    }

    boolean matchesPeerDomain(String domain) {
        return !normalizedPeerDomain().isBlank() && normalizedPeerDomain().equals(normalize(domain));
    }

    boolean hasPeerEndpoint() {
        return peerHost != null && !peerHost.isBlank() && peerPort > 0;
    }

    boolean hasSharedSecret() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }

    String configJson() {
        return """
            {"peerDomain":"%s","host":"%s","port":%d}
            """.formatted(
            escapeJson(normalizedPeerDomain()),
            escapeJson(peerHost == null ? "" : peerHost.trim()),
            peerPort
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
