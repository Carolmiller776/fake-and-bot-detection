package com.example.ratingbackend.service;

import com.example.ratingbackend.db.DatabaseManager;
import com.example.ratingbackend.model.BlockchainBlock;
import com.example.ratingbackend.model.RatingRecord;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RatingBackend {
    private final DatabaseManager databaseManager;
    private final List<String> knownModels = List.of("Model A", "Model B", "Model C", "Model D");

    public RatingBackend(DatabaseManager databaseManager) throws SQLException {
        this.databaseManager = databaseManager;
        if (getBlockchainCount() == 0) {
            createGenesisBlock();
        }
    }

    public List<String> getKnownModels() {
        return knownModels;
    }

    public void addKnownBotIp(String ipAddress) {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "INSERT OR IGNORE INTO blocked_ips(ip_address) VALUES(?)")) {
            statement.setString(1, ipAddress);
            statement.executeUpdate();
            addBlockchainBlock("Mark IP as bot: " + ipAddress);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void registerAccount(String userId, String ipAddress, boolean verified) {
        int riskScore = verified ? 10 : 50;
        if (isIpBlocked(ipAddress)) {
            riskScore = 95;
        }

        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "INSERT INTO accounts(user_id, ip_address, verified, risk_score) VALUES(?,?,?,?) " +
                        "ON CONFLICT(user_id) DO UPDATE SET ip_address=excluded.ip_address, verified=excluded.verified, risk_score=excluded.risk_score")) {
            statement.setString(1, userId);
            statement.setString(2, ipAddress);
            statement.setInt(3, verified ? 1 : 0);
            statement.setInt(4, riskScore);
            statement.executeUpdate();
            addBlockchainBlock("Register account: " + userId + " from " + ipAddress);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getReviewCountByIp(String ipAddress) throws SQLException {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM ratings WHERE ip_address = ?")) {
            statement.setString(1, ipAddress);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int getReviewCountByIpAndModel(String ipAddress, String model) throws SQLException {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM ratings WHERE ip_address = ? AND model = ?")) {
            statement.setString(1, ipAddress);
            statement.setString(2, model);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void rateAccount(String userId, String model, String review, int rating, String ipAddress) {
        try {
            Connection connection = databaseManager.getConnection();
            connection.setAutoCommit(false);
            try {
                if (!accountExists(userId)) {
                    registerAccount(userId, ipAddress, false);
                }
                if (isIpBlocked(ipAddress)) {
                    updateRiskScore(userId, 95);
                }

                int storedRisk = getRiskScore(userId);
                int adjustedScore = storedRisk;
                if (rating <= 2) {
                    adjustedScore += 15;
                } else if (rating == 3) {
                    adjustedScore += 5;
                } else {
                    adjustedScore -= 10;
                }
                updateRiskScore(userId, Math.max(0, Math.min(100, adjustedScore)));

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ratings(user_id, model, review, rating, ip_address, timestamp) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)")) {
                    statement.setString(1, userId);
                    statement.setString(2, model);
                    statement.setString(3, review);
                    statement.setInt(4, rating);
                    statement.setString(5, ipAddress);
                    statement.executeUpdate();
                }

                addBlockchainBlock("Rate account: " + userId + " model=" + model + " rating=" + rating + " review=" + review);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> getStatusSummary() {
        Map<String, Object> summary = new HashMap<>();
        try {
            summary.put("accountCount", querySingleInt("SELECT COUNT(*) FROM accounts"));
            summary.put("ratingCount", querySingleInt("SELECT COUNT(*) FROM ratings"));
            summary.put("blockedIPs", querySingleInt("SELECT COUNT(*) FROM blocked_ips"));
            summary.put("lastBlock", getLastHash());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return summary;
    }

    private int querySingleInt(String sql) throws SQLException {
        try (Statement statement = databaseManager.getConnection().createStatement(); 
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private boolean isIpBlocked(String ipAddress) {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT 1 FROM blocked_ips WHERE ip_address = ? LIMIT 1")) {
            statement.setString(1, ipAddress);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean accountExists(String userId) throws SQLException {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT 1 FROM accounts WHERE user_id = ? LIMIT 1")) {
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int getRiskScore(String userId) throws SQLException {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT risk_score FROM accounts WHERE user_id = ?")) {
            statement.setString(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("risk_score") : 50;
            }
        }
    }

    private void updateRiskScore(String userId, int riskScore) throws SQLException {
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "UPDATE accounts SET risk_score = ? WHERE user_id = ?")) {
            statement.setInt(1, riskScore);
            statement.setString(2, userId);
            statement.executeUpdate();
        }
    }

    private int getBlockchainCount() throws SQLException {
        try (Statement statement = databaseManager.getConnection().createStatement(); 
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM blockchain")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String getLastHash() throws SQLException {
        try (Statement statement = databaseManager.getConnection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT hash FROM blockchain ORDER BY id DESC LIMIT 1")) {
            return rs.next() ? rs.getString("hash") : "0";
        }
    }

    private void createGenesisBlock() throws SQLException {
        String sql = "INSERT INTO blockchain(block_index, timestamp, data, previous_hash, hash) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(sql)) {
            BlockchainBlock block = new BlockchainBlock(0, "Genesis block", "0");
            statement.setInt(1, block.getIndex());
            statement.setString(2, block.getTimestamp());
            statement.setString(3, block.getData());
            statement.setString(4, block.getPreviousHash());
            statement.setString(5, block.getHash());
            statement.executeUpdate();
        }
    }

    private void addBlockchainBlock(String data) {
        try {
            int index = 0;
            try (Statement countStatement = databaseManager.getConnection().createStatement();
                 ResultSet countResult = countStatement.executeQuery("SELECT COUNT(*) FROM blockchain")) {
                index = countResult.next() ? countResult.getInt(1) : 0;
            }
            
            String previousHash = getLastHash();
            BlockchainBlock block = new BlockchainBlock(index, data, previousHash);

            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                    "INSERT INTO blockchain(block_index, timestamp, data, previous_hash, hash) VALUES(?,?,?,?,?)")) {
                statement.setInt(1, block.getIndex());
                statement.setString(2, block.getTimestamp());
                statement.setString(3, block.getData());
                statement.setString(4, block.getPreviousHash());
                statement.setString(5, block.getHash());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
