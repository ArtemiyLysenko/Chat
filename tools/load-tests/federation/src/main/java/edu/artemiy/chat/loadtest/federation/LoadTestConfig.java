package edu.artemiy.chat.loadtest.federation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public record LoadTestConfig(
    String runId,
    int clientCountPerSide,
    String usernamePrefix,
    String password,
    Duration deliveryTimeout,
    Path outputPath,
    NodeConfig nodeA,
    NodeConfig nodeB
) {

    private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .withZone(ZoneOffset.UTC);

    public static LoadTestConfig parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Arguments must use the form --key=value. Offending argument: " + arg);
            }
            int separatorIndex = arg.indexOf('=');
            values.put(arg.substring(2, separatorIndex), arg.substring(separatorIndex + 1));
        }

        String runId = values.getOrDefault("run-id", RUN_ID_FORMATTER.format(Instant.now()));
        int clientCount = parseInt(values.getOrDefault("client-count", "50"), "client-count");
        if (clientCount < 1) {
            throw new IllegalArgumentException("client-count must be at least 1.");
        }

        String usernamePrefix = values.getOrDefault("username-prefix", "load");
        String password = values.getOrDefault("password", "password123");
        Duration deliveryTimeout = Duration.ofSeconds(parseInt(values.getOrDefault("delivery-timeout-seconds", "20"), "delivery-timeout-seconds"));
        Path outputPath = Path.of(values.getOrDefault(
            "output",
            "tools/load-tests/federation/build/reports/federation-load-%s.json".formatted(runId)
        ));

        NodeConfig nodeA = new NodeConfig(
            "A",
            values.getOrDefault("a-http", "http://localhost:8081"),
            values.getOrDefault("a-xmpp-host", "127.0.0.1"),
            parseInt(values.getOrDefault("a-xmpp-port", "5223"), "a-xmpp-port"),
            values.getOrDefault("a-domain", "node-a.local")
        );
        NodeConfig nodeB = new NodeConfig(
            "B",
            values.getOrDefault("b-http", "http://localhost:8082"),
            values.getOrDefault("b-xmpp-host", "127.0.0.1"),
            parseInt(values.getOrDefault("b-xmpp-port", "5224"), "b-xmpp-port"),
            values.getOrDefault("b-domain", "node-b.local")
        );

        return new LoadTestConfig(runId, clientCount, usernamePrefix, password, deliveryTimeout, outputPath, nodeA, nodeB);
    }

    public record NodeConfig(
        String label,
        String httpBaseUrl,
        String xmppHost,
        int xmppPort,
        String xmppDomain
    ) {
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer.", exception);
        }
    }
}
