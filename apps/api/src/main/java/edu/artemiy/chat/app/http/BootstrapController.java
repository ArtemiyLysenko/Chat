package edu.artemiy.chat.app.http;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.artemiy.chat.app.config.ChatProperties;

@RestController
class BootstrapController {

    private final ChatProperties properties;

    BootstrapController(ChatProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/bootstrap")
    Map<String, Object> bootstrap() {
        return Map.of(
            "service", "chat-app",
            "status", "modular-monolith-bootstrap",
            "runtime", "java-25",
            "framework", "spring-boot-4",
            "nodeId", properties.getNodeId(),
            "storageRoot", properties.getStorageRoot().toString(),
            "architecture", "modular-monolith-with-protocol-adapters"
        );
    }
}
