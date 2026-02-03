package com.ninja.tags.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TagDao {
    private final SqliteDb database;

    public TagDao(SqliteDb database) {
        this.database = database;
    }

    public void initialize() throws SQLException {
        try (Connection connection = database.openConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "username TEXT NOT NULL COLLATE NOCASE" +
                    ")")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS owned_tags (" +
                    "uuid TEXT NOT NULL, " +
                    "tag_id TEXT NOT NULL, " +
                    "PRIMARY KEY(uuid, tag_id)" +
                    ")")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS equipped_tag (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "tag_id TEXT NULL" +
                    ")")) {
                stmt.execute();
            }
        } catch (SQLException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SQLException("Failed to initialize database", ex);
        }
    }

    public void upsertPlayer(UUID uuid, String username) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "INSERT INTO players(uuid, username) VALUES(?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new SQLException("Failed to upsert player", ex);
        }
    }

    public Optional<UUID> findUuidByUsername(String username) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT uuid FROM players WHERE username = ? COLLATE NOCASE")) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("uuid")));
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            throw new SQLException("Failed to find uuid by username", ex);
        }
    }

    public Set<String> getOwnedTags(UUID uuid) throws SQLException {
        Set<String> tags = new HashSet<>();
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT tag_id FROM owned_tags WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tags.add(rs.getString("tag_id").toLowerCase());
                }
            }
        } catch (Exception ex) {
            throw new SQLException("Failed to get owned tags", ex);
        }
        return tags;
    }

    public void giveTag(UUID uuid, String tagId) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "INSERT OR IGNORE INTO owned_tags(uuid, tag_id) VALUES(?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new SQLException("Failed to give tag", ex);
        }
    }

    public void giveAllTags(UUID uuid, Set<String> tagIds) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO owned_tags(uuid, tag_id) VALUES(?, ?)")) {
                for (String tagId : tagIds) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId.toLowerCase());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            connection.commit();
        } catch (Exception ex) {
            throw new SQLException("Failed to give all tags", ex);
        }
    }

    public void removeTag(UUID uuid, String tagId) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "DELETE FROM owned_tags WHERE uuid = ? AND tag_id = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId.toLowerCase());
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new SQLException("Failed to remove tag", ex);
        }
    }

    public Optional<String> getEquippedTag(UUID uuid) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "SELECT tag_id FROM equipped_tag WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String tagId = rs.getString("tag_id");
                    if (tagId != null) {
                        return Optional.of(tagId.toLowerCase());
                    }
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            throw new SQLException("Failed to get equipped tag", ex);
        }
    }

    public void setEquippedTag(UUID uuid, String tagId) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "INSERT INTO equipped_tag(uuid, tag_id) VALUES(?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET tag_id = excluded.tag_id")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, tagId == null ? null : tagId.toLowerCase());
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new SQLException("Failed to set equipped tag", ex);
        }
    }

    public void clearEquippedTag(UUID uuid) throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement stmt = connection.prepareStatement(
                 "DELETE FROM equipped_tag WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (Exception ex) {
            throw new SQLException("Failed to clear equipped tag", ex);
        }
    }
}
