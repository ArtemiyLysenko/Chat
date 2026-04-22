package edu.artemiy.chat.loadtest.federation;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamClose;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.impl.JidCreate;

public final class FederationLoadTestApplication {

    public static void main(String[] args) throws Exception {
        LoadTestConfig config = LoadTestConfig.parse(args);
        FederationLoadTestApplication application = new FederationLoadTestApplication(config);
        LoadRunResult result = application.run();
        Path absoluteOutputPath = config.outputPath().toAbsolutePath();
        if (absoluteOutputPath.getParent() != null) {
            Files.createDirectories(absoluteOutputPath.getParent());
        }
        Files.writeString(config.outputPath(), result.toJson(), UTF_8);

        System.out.println(result.toConsoleSummary());
        System.out.printf("Wrote load summary to %s%n", config.outputPath().toAbsolutePath());
        if (result.hasFailures()) {
            System.exit(1);
        }
    }

    private final LoadTestConfig config;

    FederationLoadTestApplication(LoadTestConfig config) {
        this.config = config;
    }

    LoadRunResult run() throws Exception {
        Instant startedAt = Instant.now();
        List<PairSpec> pairs = pairSpecs();
        System.out.printf(
            "Starting federation load run %s with %d client pairs (%d connected clients per side).%n",
            config.runId(),
            pairs.size(),
            config.clientCountPerSide()
        );

        Map<String, HttpSessionClient> nodeASessions = new LinkedHashMap<>();
        Map<String, HttpSessionClient> nodeBSessions = new LinkedHashMap<>();
        Map<String, XmppLoadClient> nodeAConnections = new LinkedHashMap<>();
        Map<String, XmppLoadClient> nodeBConnections = new LinkedHashMap<>();

        try {
            provisionNode(config.nodeA(), pairs, nodeASessions);
            provisionNode(config.nodeB(), pairs, nodeBSessions);
            establishFriendships(config.nodeA(), pairs, nodeASessions);
            establishFriendships(config.nodeB(), pairs, nodeBSessions);

            nodeAConnections.putAll(openXmppClients(
                config.nodeA(),
                pairs.stream().map(PairSpec::userA).toList()
            ));
            nodeBConnections.putAll(openXmppClients(
                config.nodeB(),
                pairs.stream().map(PairSpec::userB).toList()
            ));

            DirectionResult aToB = runDirection(
                "A_TO_B",
                pairs,
                nodeAConnections,
                nodeBConnections,
                true
            );
            DirectionResult bToA = runDirection(
                "B_TO_A",
                pairs,
                nodeBConnections,
                nodeAConnections,
                false
            );

            Instant finishedAt = Instant.now();
            return new LoadRunResult(
                config.runId(),
                startedAt,
                finishedAt,
                config.clientCountPerSide(),
                config.outputPath().toString(),
                config.nodeA(),
                config.nodeB(),
                pairs.size() * 2,
                nodeAConnections.size(),
                nodeBConnections.size(),
                aToB,
                bToA,
                List.of(
                    "Each pair uses mirrored usernames on both nodes because the current federation mapping reuses local direct-message eligibility on both sides.",
                    "Provisioning uses the governed HTTP registration, login, and friend-request flows. Message delivery uses XMPP only."
                )
            );
        }
        finally {
            nodeAConnections.values().forEach(XmppLoadClient::close);
            nodeBConnections.values().forEach(XmppLoadClient::close);
        }
    }

    private List<PairSpec> pairSpecs() {
        List<PairSpec> pairs = new ArrayList<>(config.clientCountPerSide());
        for (int index = 1; index <= config.clientCountPerSide(); index++) {
            String slug = "%s-%s-%03d".formatted(config.usernamePrefix(), config.runId(), index);
            pairs.add(new PairSpec(index, slug + "-a", slug + "-b"));
        }
        return pairs;
    }

    private void provisionNode(
        LoadTestConfig.NodeConfig node,
        List<PairSpec> pairs,
        Map<String, HttpSessionClient> sessions
    ) throws IOException, InterruptedException {
        System.out.printf("Provisioning node %s users over HTTP %s%n", node.label(), node.httpBaseUrl());
        for (PairSpec pair : pairs) {
            registerAndLogin(node, pair.userA(), sessions);
            registerAndLogin(node, pair.userB(), sessions);
        }
    }

    private void registerAndLogin(
        LoadTestConfig.NodeConfig node,
        String username,
        Map<String, HttpSessionClient> sessions
    ) throws IOException, InterruptedException {
        String email = username + "@example.com";
        HttpSessionClient registerClient = new HttpSessionClient(node.httpBaseUrl());
        registerClient.register(email, username, config.password());

        HttpSessionClient sessionClient = new HttpSessionClient(node.httpBaseUrl());
        sessionClient.login(email, config.password());
        sessions.put(username, sessionClient);
    }

    private void establishFriendships(
        LoadTestConfig.NodeConfig node,
        List<PairSpec> pairs,
        Map<String, HttpSessionClient> sessions
    ) throws IOException, InterruptedException {
        System.out.printf("Creating friendships on node %s%n", node.label());
        for (PairSpec pair : pairs) {
            HttpSessionClient initiator = sessions.get(pair.userA());
            HttpSessionClient recipient = sessions.get(pair.userB());
            String requestId = initiator.createFriendRequest(pair.userB());
            recipient.acceptFriendRequest(requestId);
        }
    }

    private Map<String, XmppLoadClient> openXmppClients(
        LoadTestConfig.NodeConfig node,
        List<String> usernames
    ) throws InterruptedException, ExecutionException {
        System.out.printf("Opening %d XMPP clients against node %s (%s:%d)%n", usernames.size(), node.label(), node.xmppHost(), node.xmppPort());
        Map<String, XmppLoadClient> clients = new ConcurrentHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (String username : usernames) {
                futures.add(executor.submit(() -> clients.put(
                    username,
                    XmppLoadClient.connect(node, username, config.password(), config.deliveryTimeout())
                )));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        return new LinkedHashMap<>(clients);
    }

    private DirectionResult runDirection(
        String direction,
        List<PairSpec> pairs,
        Map<String, XmppLoadClient> senders,
        Map<String, XmppLoadClient> recipients,
        boolean aToB
    ) throws InterruptedException, ExecutionException {
        System.out.printf("Running direction %s with %d sends%n", direction, pairs.size());
        List<MessageResult> results = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (PairSpec pair : pairs) {
                futures.add(executor.submit(() -> {
                    String senderUsername = aToB ? pair.userA() : pair.userB();
                    String recipientUsername = aToB ? pair.userB() : pair.userA();
                    String recipientDomain = aToB ? config.nodeB().xmppDomain() : config.nodeA().xmppDomain();
                    XmppLoadClient sender = senders.get(senderUsername);
                    XmppLoadClient recipient = recipients.get(recipientUsername);
                    results.add(sendOne(direction, pair.index(), sender, recipient, recipientUsername + "@" + recipientDomain));
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        List<MessageResult> sorted = results.stream()
            .sorted((left, right) -> Integer.compare(left.index(), right.index()))
            .toList();
        List<Long> latencies = sorted.stream()
            .filter(MessageResult::success)
            .map(MessageResult::latencyMillis)
            .filter(latency -> latency > 0)
            .toList();
        List<String> failures = sorted.stream()
            .filter(result -> !result.success())
            .map(result -> "%s #%03d %s".formatted(result.direction(), result.index(), result.detail()))
            .toList();

        return new DirectionResult(
            direction,
            sorted.size(),
            (int) sorted.stream().filter(MessageResult::success).count(),
            (int) sorted.stream().filter(result -> !result.success()).count(),
            LatencySummary.from(latencies),
            failures
        );
    }

    private MessageResult sendOne(
        String direction,
        int index,
        XmppLoadClient sender,
        XmppLoadClient recipient,
        String recipientJid
    ) {
        String body = "%s:%s:%03d:%s".formatted(config.runId(), direction.toLowerCase(), index, UUID.randomUUID());
        String stanzaId = UUID.randomUUID().toString();
        CompletableFuture<Instant> receiptFuture = recipient.expectBody(body);
        CompletableFuture<String> errorFuture = sender.expectError(stanzaId);
        long startedNanos = System.nanoTime();
        try {
            sender.sendMessage(recipientJid, stanzaId, body);
            Object firstResult = CompletableFuture.anyOf(receiptFuture, errorFuture)
                .get(config.deliveryTimeout().toMillis(), TimeUnit.MILLISECONDS);
            long latencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            if (firstResult instanceof Instant) {
                return MessageResult.success(index, direction, latencyMillis, body);
            }
            return MessageResult.failure(index, direction, latencyMillis, "sender received %s".formatted(firstResult));
        }
        catch (TimeoutException exception) {
            return MessageResult.failure(index, direction, -1, "timed out waiting for delivery");
        }
        catch (Exception exception) {
            return MessageResult.failure(index, direction, -1, rootCauseMessage(exception));
        }
        finally {
            recipient.clearExpectedBody(body);
            sender.clearExpectedError(stanzaId);
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private record PairSpec(int index, String userA, String userB) {
    }

    private record MessageResult(
        int index,
        String direction,
        boolean success,
        long latencyMillis,
        String detail
    ) {
        static MessageResult success(int index, String direction, long latencyMillis, String detail) {
            return new MessageResult(index, direction, true, latencyMillis, detail);
        }

        static MessageResult failure(int index, String direction, long latencyMillis, String detail) {
            return new MessageResult(index, direction, false, latencyMillis, detail);
        }
    }

    private record DirectionResult(
        String direction,
        int attempted,
        int succeeded,
        int failed,
        LatencySummary latency,
        List<String> failures
    ) {
    }

    private record LoadRunResult(
        String runId,
        Instant startedAt,
        Instant finishedAt,
        int clientCountPerSide,
        String outputPath,
        LoadTestConfig.NodeConfig nodeA,
        LoadTestConfig.NodeConfig nodeB,
        int provisionedUsersPerNode,
        int connectedClientsOnNodeA,
        int connectedClientsOnNodeB,
        DirectionResult aToB,
        DirectionResult bToA,
        List<String> notes
    ) {
        boolean hasFailures() {
            return aToB.failed() > 0 || bToA.failed() > 0;
        }

        String toConsoleSummary() {
            return """
                Federation load run %s
                  clients per side: %d
                  provisioned users per node: %d
                  connected clients: node A %d, node B %d
                  A->B: %d/%d succeeded, p95 %d ms, failures %d
                  B->A: %d/%d succeeded, p95 %d ms, failures %d
                """.formatted(
                runId,
                clientCountPerSide,
                provisionedUsersPerNode,
                connectedClientsOnNodeA,
                connectedClientsOnNodeB,
                aToB.succeeded(),
                aToB.attempted(),
                aToB.latency().p95Millis(),
                aToB.failed(),
                bToA.succeeded(),
                bToA.attempted(),
                bToA.latency().p95Millis(),
                bToA.failed()
            );
        }

        String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append("{\n");
            appendField(builder, "runId", runId, true);
            appendField(builder, "startedAt", startedAt.toString(), true);
            appendField(builder, "finishedAt", finishedAt.toString(), true);
            appendField(builder, "clientCountPerSide", clientCountPerSide, true);
            appendField(builder, "outputPath", outputPath, true);
            appendNode(builder, "nodeA", nodeA, true);
            appendNode(builder, "nodeB", nodeB, true);
            appendField(builder, "provisionedUsersPerNode", provisionedUsersPerNode, true);
            appendField(builder, "connectedClientsOnNodeA", connectedClientsOnNodeA, true);
            appendField(builder, "connectedClientsOnNodeB", connectedClientsOnNodeB, true);
            appendDirection(builder, "aToB", aToB, true);
            appendDirection(builder, "bToA", bToA, true);
            appendStringArray(builder, "notes", notes, false);
            builder.append("}\n");
            return builder.toString();
        }

        private static void appendNode(StringBuilder builder, String field, LoadTestConfig.NodeConfig node, boolean trailingComma) {
            builder.append("  \"").append(field).append("\": {\n");
            appendField(builder, "label", node.label(), true, 4);
            appendField(builder, "httpBaseUrl", node.httpBaseUrl(), true, 4);
            appendField(builder, "xmppHost", node.xmppHost(), true, 4);
            appendField(builder, "xmppPort", node.xmppPort(), true, 4);
            appendField(builder, "xmppDomain", node.xmppDomain(), false, 4);
            builder.append("  }");
            if (trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }

        private static void appendDirection(StringBuilder builder, String field, DirectionResult result, boolean trailingComma) {
            builder.append("  \"").append(field).append("\": {\n");
            appendField(builder, "direction", result.direction(), true, 4);
            appendField(builder, "attempted", result.attempted(), true, 4);
            appendField(builder, "succeeded", result.succeeded(), true, 4);
            appendField(builder, "failed", result.failed(), true, 4);
            appendLatency(builder, result.latency());
            if (!result.failures().isEmpty()) {
                builder.append(",\n");
                appendStringArray(builder, "failures", result.failures(), false, 4);
                builder.append('\n');
            }
            else {
                builder.append('\n');
            }
            builder.append("  }");
            if (trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }

        private static void appendLatency(StringBuilder builder, LatencySummary latency) {
            builder.append("    \"latency\": {\n");
            appendField(builder, "minMillis", latency.minMillis(), true, 6);
            appendField(builder, "p50Millis", latency.p50Millis(), true, 6);
            appendField(builder, "p95Millis", latency.p95Millis(), true, 6);
            appendField(builder, "p99Millis", latency.p99Millis(), true, 6);
            appendField(builder, "maxMillis", latency.maxMillis(), true, 6);
            appendField(builder, "averageMillis", latency.averageMillis(), false, 6);
            builder.append("    }");
        }

        private static void appendStringArray(StringBuilder builder, String field, List<String> values, boolean trailingComma) {
            appendStringArray(builder, field, values, trailingComma, 2);
        }

        private static void appendStringArray(StringBuilder builder, String field, List<String> values, boolean trailingComma, int indent) {
            String padding = " ".repeat(indent);
            builder.append(padding).append('"').append(field).append("\": [");
            if (!values.isEmpty()) {
                builder.append('\n');
                for (int index = 0; index < values.size(); index++) {
                    builder.append(padding).append("  \"").append(escape(values.get(index))).append('"');
                    if (index < values.size() - 1) {
                        builder.append(',');
                    }
                    builder.append('\n');
                }
                builder.append(padding);
            }
            builder.append(']');
            if (trailingComma) {
                builder.append(',');
            }
        }

        private static void appendField(StringBuilder builder, String name, String value, boolean trailingComma) {
            appendField(builder, name, value, trailingComma, 2);
        }

        private static void appendField(StringBuilder builder, String name, String value, boolean trailingComma, int indent) {
            builder.append(" ".repeat(indent))
                .append('"')
                .append(name)
                .append("\": \"")
                .append(escape(value))
                .append('"');
            if (trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }

        private static void appendField(StringBuilder builder, String name, int value, boolean trailingComma) {
            appendField(builder, name, value, trailingComma, 2);
        }

        private static void appendField(StringBuilder builder, String name, int value, boolean trailingComma, int indent) {
            builder.append(" ".repeat(indent))
                .append('"')
                .append(name)
                .append("\": ")
                .append(value);
            if (trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }

        private static void appendField(StringBuilder builder, String name, double value, boolean trailingComma, int indent) {
            builder.append(" ".repeat(indent))
                .append('"')
                .append(name)
                .append("\": ")
                .append(String.format(java.util.Locale.ROOT, "%.2f", value));
            if (trailingComma) {
                builder.append(',');
            }
            builder.append('\n');
        }

        private static String escape(String value) {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        }
    }

    private static final class HttpSessionClient {

        private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("\"requestId\":\"([^\"]+)\"");

        private final String baseUrl;
        private final CookieManager cookieManager = new CookieManager();
        private final HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpSessionClient(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        void register(String email, String username, String password) throws IOException, InterruptedException {
            fetchPage("/register");
            HttpResponse<String> response = postJson(
                "/api/auth/register",
                """
                    {"email":"%s","username":"%s","password":"%s"}
                    """.formatted(email, username, password)
            );
            if (response.statusCode() != 201 && response.statusCode() != 409) {
                throw new IllegalStateException("Register failed for %s with status %d: %s".formatted(username, response.statusCode(), response.body()));
            }
        }

        void login(String email, String password) throws IOException, InterruptedException {
            fetchPage("/login");
            HttpResponse<String> response = postJson(
                "/api/auth/login",
                """
                    {"email":"%s","password":"%s"}
                    """.formatted(email, password)
            );
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Login failed for %s with status %d: %s".formatted(email, response.statusCode(), response.body()));
            }
        }

        String createFriendRequest(String username) throws IOException, InterruptedException {
            HttpResponse<String> response = postJson(
                "/api/friend-requests",
                """
                    {"username":"%s"}
                    """.formatted(username)
            );
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new IllegalStateException("Friend request failed for %s with status %d: %s".formatted(username, response.statusCode(), response.body()));
            }
            Matcher matcher = REQUEST_ID_PATTERN.matcher(response.body());
            if (!matcher.find()) {
                throw new IllegalStateException("Friend request response did not contain requestId: " + response.body());
            }
            return matcher.group(1);
        }

        void acceptFriendRequest(String requestId) throws IOException, InterruptedException {
            HttpResponse<String> response = postWithoutBody("/api/friend-requests/%s/accept".formatted(requestId));
            if (response.statusCode() != 204) {
                throw new IllegalStateException("Friend request accept failed for %s with status %d: %s".formatted(requestId, response.statusCode(), response.body()));
            }
        }

        private void fetchPage(String path) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("User-Agent", "FederationLoadTest/1.0")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("GET %s failed with status %d".formatted(path, response.statusCode()));
            }
        }

        private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrfToken())
                .header("User-Agent", "FederationLoadTest/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        }

        private HttpResponse<String> postWithoutBody(String path) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-CSRF-TOKEN", csrfToken())
                .header("User-Agent", "FederationLoadTest/1.0")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        }

        private String csrfToken() {
            Optional<HttpCookie> token = cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .findFirst();
            return token.map(HttpCookie::getValue)
                .orElseThrow(() -> new IllegalStateException("Missing XSRF-TOKEN cookie for " + baseUrl));
        }
    }

    private static final class XmppLoadClient implements AutoCloseable {

        private final XMPPTCPConnection connection;
        private final Map<String, CompletableFuture<Instant>> bodyReceipts = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<String>> errorByStanzaId = new ConcurrentHashMap<>();

        private XmppLoadClient(XMPPTCPConnection connection) {
            this.connection = connection;
            connection.addAsyncStanzaListener(new StanzaDispatcher(), stanza -> stanza instanceof Message);
        }

        static XmppLoadClient connect(
            LoadTestConfig.NodeConfig node,
            String username,
            String password,
            Duration timeout
        ) {
            try {
                XMPPTCPConnection connection = new XMPPTCPConnection(
                    XMPPTCPConnectionConfiguration.builder()
                        .setXmppDomain(JidCreate.domainBareFrom(node.xmppDomain()))
                        .setHost(node.xmppHost())
                        .setPort(node.xmppPort())
                        .setUsernameAndPassword(username, password)
                        .setResource("load-" + username)
                        .setCompressionEnabled(false)
                        .setSendPresence(false)
                        .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                        .build()
                );
                Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false);
                connection.setReplyTimeout(timeout.toMillis());
                connection.connect().login();
                return new XmppLoadClient(connection);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("XMPP login interrupted for %s on %s:%d".formatted(username, node.xmppHost(), node.xmppPort()), exception);
            }
            catch (SmackException | IOException | XMPPException exception) {
                throw new IllegalStateException("XMPP login failed for %s on %s:%d".formatted(username, node.xmppHost(), node.xmppPort()), exception);
            }
        }

        CompletableFuture<Instant> expectBody(String body) {
            CompletableFuture<Instant> future = new CompletableFuture<>();
            bodyReceipts.put(body, future);
            return future;
        }

        void clearExpectedBody(String body) {
            bodyReceipts.remove(body);
        }

        CompletableFuture<String> expectError(String stanzaId) {
            CompletableFuture<String> future = new CompletableFuture<>();
            errorByStanzaId.put(stanzaId, future);
            return future;
        }

        void clearExpectedError(String stanzaId) {
            errorByStanzaId.remove(stanzaId);
        }

        void sendMessage(String recipientJid, String stanzaId, String body) throws Exception {
            Message message = new Message(JidCreate.entityBareFrom(recipientJid), Message.Type.chat);
            message.setStanzaId(stanzaId);
            message.setBody(body);
            connection.sendStanza(message);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void close() {
            try {
                if (connection.isConnected()) {
                    try {
                        connection.sendNonza(StreamClose.INSTANCE);
                    }
                    catch (Exception ignored) {
                    }
                    connection.instantShutdown();
                }
            }
            catch (Exception ignored) {
            }
        }

        private final class StanzaDispatcher implements StanzaListener {

            @Override
            public void processStanza(Stanza stanza) {
                if (!(stanza instanceof Message message)) {
                    return;
                }
                if (Message.Type.error.equals(message.getType())) {
                    CompletableFuture<String> future = errorByStanzaId.remove(message.getStanzaId());
                    if (future != null) {
                        String detail = message.getError() == null || message.getError().getCondition() == null
                            ? "unknown-error"
                            : message.getError().getCondition().toString();
                        future.complete(detail);
                    }
                    return;
                }
                if (!Message.Type.chat.equals(message.getType()) || message.getBody() == null) {
                    return;
                }
                CompletableFuture<Instant> future = bodyReceipts.remove(message.getBody());
                if (future != null) {
                    future.complete(Instant.now());
                }
            }
        }
    }
}
