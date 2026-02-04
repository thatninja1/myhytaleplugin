package com.ninja.tags.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteDb {
    private final Path databasePath;

    public SqliteDb(Path dataFolder) {
        this.databasePath = dataFolder.resolve("ninjatags.db");
    }

    public Connection openConnection() throws SQLException, IOException {
        Files.createDirectories(databasePath.getParent());
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }
}
