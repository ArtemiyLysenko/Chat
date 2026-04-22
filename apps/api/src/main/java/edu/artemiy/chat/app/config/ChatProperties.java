package edu.artemiy.chat.app.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    private Path storageRoot = Path.of("storage");
    private String nodeId = "local-node";
    private final Auth auth = new Auth();
    private final Federation federation = new Federation();

    public Path getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Auth getAuth() {
        return auth;
    }

    public Federation getFederation() {
        return federation;
    }

    public static class Auth {

        private String cookieName = "CHAT_SESSION";
        private Duration sessionTtl = Duration.ofDays(30);
        private Duration passwordResetTtl = Duration.ofMinutes(15);
        private boolean secureCookie = true;
        private List<String> adminUsernames = new ArrayList<>();

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public Duration getSessionTtl() {
            return sessionTtl;
        }

        public void setSessionTtl(Duration sessionTtl) {
            this.sessionTtl = sessionTtl;
        }

        public Duration getPasswordResetTtl() {
            return passwordResetTtl;
        }

        public void setPasswordResetTtl(Duration passwordResetTtl) {
            this.passwordResetTtl = passwordResetTtl;
        }

        public boolean isSecureCookie() {
            return secureCookie;
        }

        public void setSecureCookie(boolean secureCookie) {
            this.secureCookie = secureCookie;
        }

        public List<String> getAdminUsernames() {
            return adminUsernames;
        }

        public void setAdminUsernames(List<String> adminUsernames) {
            this.adminUsernames = adminUsernames == null ? new ArrayList<>() : new ArrayList<>(adminUsernames);
        }
    }

    public static class Federation {

        private boolean enabled;
        private String peerDomain = "";

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
    }
}
