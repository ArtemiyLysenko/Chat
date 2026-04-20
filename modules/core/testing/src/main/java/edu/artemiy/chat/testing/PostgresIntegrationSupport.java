package edu.artemiy.chat.testing;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresIntegrationSupport {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        configureDockerDesktopOverrides();
        POSTGRES.start();
    }

    protected static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static void configureDockerDesktopOverrides() {
        if (System.getProperty("docker.host") != null) {
            return;
        }

        var rawSocket = Path.of(
            System.getProperty("user.home"),
            "Library",
            "Containers",
            "com.docker.docker",
            "Data",
            "docker.raw.sock"
        );
        var userSocket = Path.of(System.getProperty("user.home"), ".docker", "run", "docker.sock");

        if (!Files.exists(userSocket) && !Files.exists(rawSocket)) {
            return;
        }

        var socket = Files.exists(userSocket) ? userSocket : rawSocket;
        System.setProperty("docker.host", "unix://" + socket.toAbsolutePath());
        System.setProperty("dockerconfig.source", "autoIgnoringUserProperties");
        System.setProperty(
            "docker.client.strategy",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy"
        );

        if (System.getProperty("docker.api.version") == null) {
            System.setProperty("docker.api.version", "1.41");
        }
    }
}
