package de.playground.outbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The classic approach: remember the highest {@code updated_at} seen so far and next time
 * ask for everything after it.
 *
 * <p><strong>Broken:</strong> {@code updated_at} is assigned before commit. A transaction that
 * commits late makes a row visible whose timestamp is already behind the cursor, and that row
 * is never read.
 */
public final class TimestampCursorFeed implements ChangeFeed {

    private static final String SQL =
            "SELECT name, updated_at FROM item WHERE updated_at > ? ORDER BY updated_at";

    private final List<String> delivered = new ArrayList<>();
    private LocalDateTime cursor = Items.EPOCH;   // "everything up to here is done"

    @Override
    public List<String> poll(Connection connection) throws SQLException {
        List<String> batch = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SQL)) {
            ps.setObject(1, cursor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    batch.add(rs.getString("name"));
                    cursor = rs.getObject("updated_at", LocalDateTime.class);
                }
            }
        }
        delivered.addAll(batch);
        return batch;
    }

    @Override
    public List<String> delivered() {
        return List.copyOf(delivered);
    }

    public LocalDateTime cursor() {
        return cursor;
    }
}
