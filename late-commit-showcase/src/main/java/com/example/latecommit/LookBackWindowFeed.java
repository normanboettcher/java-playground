package com.example.latecommit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The common "fix": re-read a safety window behind the cursor and skip rows already delivered.
 *
 * <p><strong>Only probabilistic:</strong> it catches every transaction that commits within the
 * window, and silently misses every transaction that stays open longer than that.
 */
public final class LookBackWindowFeed implements ChangeFeed {

    private static final String SQL =
            "SELECT id, name, updated_at FROM item WHERE updated_at > ? ORDER BY updated_at";

    private final Duration window;
    private final List<String> delivered = new ArrayList<>();
    private final Set<Long> seen = new HashSet<>();   // the window re-reads rows, so delivery must be idempotent
    private LocalDateTime cursor = Items.EPOCH;

    public LookBackWindowFeed(Duration window) {
        this.window = window;
    }

    @Override
    public List<String> poll(Connection connection) throws SQLException {
        List<String> batch = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SQL)) {
            ps.setObject(1, cursor.minus(window));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (seen.add(rs.getLong("id"))) {
                        batch.add(rs.getString("name"));
                    }
                    LocalDateTime updatedAt = rs.getObject("updated_at", LocalDateTime.class);
                    if (updatedAt.isAfter(cursor)) {
                        cursor = updatedAt;   // a re-read older row must not move the cursor back
                    }
                }
            }
        }
        // A real implementation would also evict ids older than (cursor - window) from 'seen'.
        delivered.addAll(batch);
        return batch;
    }

    @Override
    public List<String> delivered() {
        return List.copyOf(delivered);
    }
}
