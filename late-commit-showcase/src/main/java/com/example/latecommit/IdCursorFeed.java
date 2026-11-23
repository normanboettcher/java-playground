package com.example.latecommit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "we don't trust clocks, so we use the auto-increment id" approach.
 *
 * <p><strong>Broken in exactly the same way:</strong> the id is allocated when the row is
 * inserted, not when it is committed. No clock is involved, and the late commit is still lost.
 */
public final class IdCursorFeed implements ChangeFeed {

    private static final String SQL = "SELECT id, name FROM item WHERE id > ? ORDER BY id";

    private final List<String> delivered = new ArrayList<>();
    private long cursor = 0;   // "everything up to here is done"

    @Override
    public List<String> poll(Connection connection) throws SQLException {
        List<String> batch = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SQL)) {
            ps.setLong(1, cursor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    batch.add(rs.getString("name"));
                    cursor = rs.getLong("id");
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

    public long cursor() {
        return cursor;
    }
}
