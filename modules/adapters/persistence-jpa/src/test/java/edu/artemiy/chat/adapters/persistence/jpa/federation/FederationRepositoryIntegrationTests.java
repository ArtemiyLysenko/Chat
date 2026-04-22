package edu.artemiy.chat.adapters.persistence.jpa.federation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.adapters.persistence.jpa.PersistenceJpaTestApplication;
import edu.artemiy.chat.federation.spi.FederationPersistencePort;
import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest(classes = PersistenceJpaTestApplication.class)
@ActiveProfiles("test")
class FederationRepositoryIntegrationTests extends PostgresIntegrationSupport {

    private static final Instant NOW = Instant.parse("2026-04-22T10:15:00Z");

    @Autowired
    private FederationPersistencePort federationPersistencePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @BeforeEach
    void cleanDatabase() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbcTemplate.execute(
            """
                truncate table
                    federation_traffic_samples,
                    federation_peers,
                    xmpp_client_sessions
                cascade
                """
        );
    }

    @Test
    void appendsConcurrentTrafficSamplesWithoutPeerInsertCollisionsOrCounterLoss() throws Exception {
        int taskCount = 24;
        CyclicBarrier startBarrier = new CyclicBarrier(taskCount);
        List<Future<?>> futures = new ArrayList<>(taskCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < taskCount; index++) {
                int sampleIndex = index;
                futures.add(executor.submit(() -> {
                    startBarrier.await();
                    federationPersistencePort.appendFederationTrafficSample(
                        "node-b.local",
                        "{\"host\":\"127.0.0.1\",\"port\":5224}",
                        1,
                        1,
                        1,
                        1,
                        sampleIndex % 4 == 0 ? 1 : 0,
                        NOW.plusMillis(sampleIndex)
                    );
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from federation_peers", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from federation_traffic_samples", Integer.class)).isEqualTo(taskCount);
        assertThat(jdbcTemplate.queryForObject("select max(inbound_messages) from federation_traffic_samples", Long.class)).isEqualTo(taskCount);
        assertThat(jdbcTemplate.queryForObject("select max(outbound_messages) from federation_traffic_samples", Long.class)).isEqualTo(taskCount);
        assertThat(jdbcTemplate.queryForObject("select max(inbound_stanzas) from federation_traffic_samples", Long.class)).isEqualTo(taskCount);
        assertThat(jdbcTemplate.queryForObject("select max(outbound_stanzas) from federation_traffic_samples", Long.class)).isEqualTo(taskCount);
        assertThat(jdbcTemplate.queryForObject("select max(error_count) from federation_traffic_samples", Long.class)).isEqualTo(6L);
    }
}
