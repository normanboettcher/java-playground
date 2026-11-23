package de.playground.outbox;

import java.util.List;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * MariaDB defaults to REPEATABLE READ. That doesn't change the outcome: the poller reads in
 * autocommit mode, so every poll is its own transaction and sees everything committed before it.
 */
@Testcontainers
class MariaDbLateCommitTest extends LateCommitScenarios {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4");

    @Override
    protected JdbcDatabaseContainer<?> database() {
        return MARIADB;
    }

    @Override
    protected List<String> schema() {
        return List.of(
                "DROP TABLE IF EXISTS outbox",
                "DROP TABLE IF EXISTS item",
                """
                CREATE TABLE item (
                  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
                  name       VARCHAR(100) NOT NULL UNIQUE,
                  updated_at DATETIME(6) NOT NULL
                ) ENGINE = InnoDB""",
                """
                CREATE TABLE outbox (
                  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
                  item_id BIGINT NOT NULL,
                  FOREIGN KEY (item_id) REFERENCES item (id)
                ) ENGINE = InnoDB""");
    }
}
