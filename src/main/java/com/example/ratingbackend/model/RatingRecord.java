package com.example.ratingbackend.model;

import java.time.Instant;

public class RatingRecord {
    private final String userId;
    private final String model;
    private final String review;
    private final int rating;
    private final String ipAddress;
    private final Instant timestamp;

    public RatingRecord(String userId, String model, String review, int rating, String ipAddress) {
        this.userId = userId;
        this.model = model;
        this.review = review;
        this.rating = rating;
        this.ipAddress = ipAddress;
        this.timestamp = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getModel() {
        return model;
    }

    public String getReview() {
        return review;
    }

    public int getRating() {
        return rating;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
