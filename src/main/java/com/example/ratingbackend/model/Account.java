package com.example.ratingbackend.model;

public class Account {
    private final String userId;
    private final String ipAddress;
    private boolean verified;
    private int riskScore;

    public Account(String userId, String ipAddress, boolean verified) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.verified = verified;
        this.riskScore = verified ? 10 : 50;
    }

    public String getUserId() {
        return userId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    @Override
    public String toString() {
        return "Account{" +
                "userId='" + userId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", verified=" + verified +
                ", riskScore=" + riskScore +
                '}';
    }
}
