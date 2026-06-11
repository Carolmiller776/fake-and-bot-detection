package com.example.ratingbackend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager implements AutoCloseable {
    private final Connection connection;

    public DatabaseManager(String databaseFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        connection.setAutoCommit(true);
        initializeDatabase();
    }

    public Connection getConnection() {
        return connection;
    }

    private void initializeDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                    "user_id TEXT PRIMARY KEY, " +
                    "ip_address TEXT, " +
                    "verified INTEGER, " +
                    "risk_score INTEGER)");
            statement.execute("CREATE TABLE IF NOT EXISTS blocked_ips (" +
                    "ip_address TEXT PRIMARY KEY)");
            statement.execute("CREATE TABLE IF NOT EXISTS ratings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT, " +
                    "model TEXT, " +
                    "review TEXT, " +
                    "rating INTEGER, " +
                    "ip_address TEXT, " +
                    "timestamp TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS blockchain (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "block_index INTEGER, " +
                    "timestamp TEXT, " +
                    "data TEXT, " +
                    "previous_hash TEXT, " +
                    "hash TEXT)");
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
