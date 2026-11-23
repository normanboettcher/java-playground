package de.playground.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * A strategy that polls the database for changes and delivers them downstream:
 * to a search index, a cache, a data warehouse or another service.
 *
 * <p>Every implementation in this project answers the same question differently:
 * <em>"How do I know which changes I have not delivered yet?"</em>
 */
public interface ChangeFeed {

    /** Reads changes this feed has not delivered yet, delivers them and returns them. */
    List<String> poll(Connection connection) throws SQLException;

    /** Everything delivered so far, in delivery order. */
    List<String> delivered();
}
