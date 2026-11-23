package com.example.latecommit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** The write side: how the application stores items. Plain JDBC, standard SQL. */
public final class Items {

    /** Start value for timestamp cursors. */
    public static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private Items() { }

    /**
     * A plain write. {@code updatedAt} is set when the statement runs, exactly like an
     * application (or a column default) stamps {@code updated_at}: at write time, not at commit time.
     */
    public static void insert(Connection c, String name, LocalDateTime updatedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO item (name, updated_at) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setObject(2, updatedAt);
            ps.executeUpdate();
        }
    }

    /**
     * The transactional outbox write: the business row and its change marker in the
     * <em>same</em> transaction. Both become visible together at commit, or not at all.
     */
    public static void insertWithOutbox(Connection c, String name, LocalDateTime updatedAt) throws SQLException {
        if (c.getAutoCommit()) {
            throw new IllegalStateException("insertWithOutbox must run inside a transaction");
        }
        insert(c, name, updatedAt);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO outbox (item_id) SELECT id FROM item WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    /** The generated id of an item, for assertions about allocation order. */
    public static long idOf(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM item WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No item named " + name);
                }
                return rs.getLong(1);
            }
        }
    }
}
