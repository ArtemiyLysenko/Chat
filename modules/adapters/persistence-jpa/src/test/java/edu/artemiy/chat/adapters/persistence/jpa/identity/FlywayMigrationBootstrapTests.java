package edu.artemiy.chat.adapters.persistence.jpa.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationBootstrapTests {

    @Test
    void migratesIdentitySchemaFromEmptyPostgresDatabase() throws Exception {
        try (var postgres = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                     select table_name
                     from information_schema.tables
                     where table_schema = 'public'
                       and table_name in ('users', 'password_reset_tokens', 'user_sessions')
                     order by table_name
                     """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("table_name")).isEqualTo("password_reset_tokens");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("table_name")).isEqualTo("user_sessions");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("table_name")).isEqualTo("users");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }
}
