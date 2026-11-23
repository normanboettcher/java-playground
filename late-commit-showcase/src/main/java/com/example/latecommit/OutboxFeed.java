package com.example.latecommit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The transactional outbox: every change leaves a marker row, written in the same transaction
 * as the change itself. The feed delivers whatever markers exist and deletes them afterwards.
 *
 * <p><strong>Correct:</strong> there is no cursor that could fall behind. A marker exists until
 * it has been delivered, no matter when its transaction commits or which timestamp it carries.
 *
 * <p>Deliberately minimal: a single poller and at-least-once delivery (a crash between delivery
 * and delete delivers again, so consumers must be idempotent). Several concurrent pollers would
 * claim markers with {@code SELECT ... FOR UPDATE SKIP LOCKED}, with a lease and retries.
 */
public final class OutboxFeed implements ChangeFeed {

    private static final String READ =
            "SELECT o.id, i.name FROM outbox o JOIN item i ON i.id = o.item_id ORDER BY o.id";
    private static final String ACK = "DELETE FROM outbox WHERE id = ?";

    private final List<String> delivered = new ArrayList<>();

    @Override
    public List<String> poll(Connection connection) throws SQLException {
        List<String> batch = new ArrayList<>();
        List<Long> markers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(READ);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                markers.add(rs.getLong("id"));
                batch.add(rs.getString("name"));
            }
        }
        delivered.addAll(batch);                  // 1. deliver ...
        try (PreparedStatement ps = connection.prepareStatement(ACK)) {
            for (long marker : markers) {         // 2. ... then acknowledge by deleting the marker
                ps.setLong(1, marker);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return batch;
    }

    @Override
    public List<String> delivered() {
        return List.copyOf(delivered);
    }

    /** Markers not delivered yet. */
    public static int pending(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM outbox");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
