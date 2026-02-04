package com.ninja.tags.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteDb {
    private final Path databasePath;
    private static volatile boolean driverLoaded;

    public SqliteDb(Path dataFolder) {
        this.databasePath = dataFolder.resolve("ninjatags.db");
    }

    public Connection openConnection() throws SQLException, IOException {
        ensureDriverLoaded();
        Files.createDirectories(databasePath.getParent());
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private static void ensureDriverLoaded() throws SQLException {
        if (driverLoaded) {
            return;
        }
        synchronized (SqliteDb.class) {
            if (driverLoaded) {
                return;
            }
            try {
                Class.forName("org.sqlite.JDBC");
                driverLoaded = true;
            } catch (ClassNotFoundException ex) {
                throw new SQLException("SQLite JDBC driver not found", ex);
            }
        }
    }
}
