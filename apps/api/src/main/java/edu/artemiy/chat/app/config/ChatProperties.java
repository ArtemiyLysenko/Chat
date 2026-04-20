package edu.artemiy.chat.app.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    private Path storageRoot = Path.of("storage");
    private String nodeId = "local-node";
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

    public Federation getFederation() {
        return federation;
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
