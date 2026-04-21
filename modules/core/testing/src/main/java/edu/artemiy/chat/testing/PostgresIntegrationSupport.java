package edu.artemiy.chat.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresIntegrationSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    protected static final PostgreSQLContainer<?> POSTGRES = newPostgresContainer();

    static {
        POSTGRES.start();
    }

    protected static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public static PostgreSQLContainer<?> newPostgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }

    protected static PostgresDatabase createIsolatedDatabase(String namePrefix) {
        String sanitizedPrefix = namePrefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String databaseName = sanitizedPrefix + "_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + databaseName);
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to create isolated PostgreSQL database " + databaseName, exception);
        }

        return new PostgresDatabase(jdbcUrlForDatabase(databaseName), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String adminJdbcUrl() {
        return jdbcUrlForDatabase("postgres");
    }

    private static String jdbcUrlForDatabase(String databaseName) {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        int querySeparator = jdbcUrl.indexOf('?');
        String baseUrl = querySeparator >= 0 ? jdbcUrl.substring(0, querySeparator) : jdbcUrl;
        String querySuffix = querySeparator >= 0 ? jdbcUrl.substring(querySeparator) : "";
        int databaseSeparator = baseUrl.lastIndexOf('/');
        return baseUrl.substring(0, databaseSeparator + 1) + databaseName + querySuffix;
    }

    protected record PostgresDatabase(String jdbcUrl, String username, String password) {
    }
}
