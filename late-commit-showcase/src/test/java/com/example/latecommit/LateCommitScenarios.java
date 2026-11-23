package com.example.latecommit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * The same five scenarios, run against every database (see the subclasses).
 *
 * <p>Every scenario is <strong>deterministic</strong>: no threads, no sleeps, no timing luck.
 * Three connections play three roles, and the test decides the order in which things happen:
 * <ul>
 *   <li>{@code slow}: a transaction that writes first but commits last,</li>
 *   <li>{@code fast}: a transaction that writes later and commits immediately,</li>
 *   <li>{@code poller}: the change feed, reading committed data only.</li>
 * </ul>
 *
 * <p>The tests are green when the <em>documented behaviour</em> occurs, including the broken
 * behaviour. Each test is executable documentation of one failure mode, or of its fix.
 */
abstract class LateCommitScenarios {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

    /** 10:00:00 plus the given seconds. Set explicitly, like an application stamping updated_at at write time. */
    static LocalDateTime at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    protected abstract JdbcDatabaseContainer<?> database();

    /** Vendor-specific DDL. Everything else in this project is identical for all databases. */
    protected abstract List<String> schema();

    protected Connection open() throws SQLException {
        JdbcDatabaseContainer<?> db = database();
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    @BeforeEach
    void freshSchema() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            for (String ddl : schema()) {
                s.execute(ddl);
            }
        }
    }

    @Test
    @DisplayName("1. Timestamp cursor: a late commit is skipped forever")
    void timestampCursorSkipsLateCommit() throws SQLException {
        TimestampCursorFeed feed = new TimestampCursorFeed();

        try (Connection slow = open(); Connection fast = open(); Connection poller = open()) {
            slow.setAutoCommit(false);
            Items.insert(slow, "A", at(0));                       // written at 10:00:00, NOT committed yet

            Items.insert(fast, "B", at(2));                       // 10:00:02, autocommit: visible now

            assertThat(feed.poll(poller)).containsExactly("B");    // the poller only sees committed rows
            assertThat(feed.cursor()).isEqualTo(at(2));            // "everything up to 10:00:02 is done"

            slow.commit();                                         // A becomes visible, stamped 10:00:00

            assertThat(feed.poll(poller)).isEmpty();               // 10:00:00 is not after 10:00:02
            assertThat(feed.delivered()).containsExactly("B");     // A is lost, and nothing reported an error
        }
    }

    @Test
    @DisplayName("2. Id cursor: the same loss, with no clock involved at all")
    void idCursorSkipsLateCommit() throws SQLException {
        IdCursorFeed feed = new IdCursorFeed();

        try (Connection slow = open(); Connection fast = open(); Connection poller = open()) {
            slow.setAutoCommit(false);
            Items.insert(slow, "A", at(0));                       // gets the SMALLER id, NOT committed yet

            Items.insert(fast, "B", at(0));                       // gets the larger id, visible now

            assertThat(feed.poll(poller)).containsExactly("B");    // cursor moves to B's id

            slow.commit();                                         // A becomes visible with an id below the cursor

            assertThat(Items.idOf(poller, "A")).isLessThan(Items.idOf(poller, "B"));
            assertThat(feed.poll(poller)).isEmpty();
            assertThat(feed.delivered()).containsExactly("B");     // A is lost: ids follow insert order, not commit order
        }
    }

    @Test
    @DisplayName("3. Look-back window: catches a late commit that fits into the window ...")
    void lookBackWindowCatchesShortTransaction() throws SQLException {
        LookBackWindowFeed feed = new LookBackWindowFeed(Duration.ofSeconds(5));

        try (Connection slow = open(); Connection fast = open(); Connection poller = open()) {
            slow.setAutoCommit(false);
            Items.insert(slow, "A", at(0));                       // 2 seconds before B: inside the 5 s window

            Items.insert(fast, "B", at(2));

            assertThat(feed.poll(poller)).containsExactly("B");

            slow.commit();

            assertThat(feed.poll(poller)).containsExactly("A");    // re-read window covers 09:59:57 onwards
            assertThat(feed.delivered()).containsExactly("B", "A");
        }
    }

    @Test
    @DisplayName("4. ... and silently misses any transaction that stays open longer than the window")
    void lookBackWindowMissesLongTransaction() throws SQLException {
        LookBackWindowFeed feed = new LookBackWindowFeed(Duration.ofSeconds(5));

        try (Connection slow = open(); Connection fast = open(); Connection poller = open()) {
            slow.setAutoCommit(false);
            Items.insert(slow, "A", at(-60));                     // a long transaction: stamped a minute earlier

            Items.insert(fast, "B", at(2));

            assertThat(feed.poll(poller)).containsExactly("B");

            slow.commit();

            assertThat(feed.poll(poller)).isEmpty();               // 09:59:00 is outside the window
            assertThat(feed.delivered()).containsExactly("B");     // the "fix" is a probability, not a guarantee
        }
    }

    @Test
    @DisplayName("5. Transactional outbox: the late commit is delivered, whatever its timestamp")
    void outboxDeliversLateCommit() throws SQLException {
        OutboxFeed feed = new OutboxFeed();

        try (Connection slow = open(); Connection fast = open(); Connection poller = open()) {
            slow.setAutoCommit(false);
            Items.insertWithOutbox(slow, "A", at(-3600));         // even stamped an hour earlier

            fast.setAutoCommit(false);
            Items.insertWithOutbox(fast, "B", at(2));
            fast.commit();

            assertThat(feed.poll(poller)).containsExactly("B");

            slow.commit();                                         // A's marker becomes visible together with A

            assertThat(feed.poll(poller)).containsExactly("A");    // no cursor to fall behind
            assertThat(feed.delivered()).containsExactly("B", "A");
            assertThat(OutboxFeed.pending(poller)).isZero();       // every marker delivered and acknowledged
        }
    }
}
