package de.playground.outbox;

import java.util.List;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresLateCommitTest extends LateCommitScenarios {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Override
    protected JdbcDatabaseContainer<?> database() {
        return POSTGRES;
    }

    @Override
    protected List<String> schema() {
        return List.of(
                "DROP TABLE IF EXISTS outbox",
                "DROP TABLE IF EXISTS item",
                """
                CREATE TABLE item (
                  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                  name       VARCHAR(100) NOT NULL UNIQUE,
                  updated_at TIMESTAMP(6) NOT NULL
                )""",
                """
                CREATE TABLE outbox (
                  id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                  item_id BIGINT NOT NULL REFERENCES item (id)
                )""");
    }
}
