package edu.artemiy.chat.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import edu.artemiy.chat.testing.PostgresIntegrationSupport;

@SpringBootTest
@ActiveProfiles("test")
class ChatApplicationTests extends PostgresIntegrationSupport {

    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry);
    }

    @Test
    void contextLoads() {
        assertThat(flyway).isNotNull();
    }
}
