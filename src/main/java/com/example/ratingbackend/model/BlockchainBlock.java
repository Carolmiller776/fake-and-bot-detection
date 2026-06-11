package com.example.ratingbackend.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public class BlockchainBlock {
    private final int index;
    private final String timestamp;
    private final String data;
    private final String previousHash;
    private final String hash;

    public BlockchainBlock(int index, String data, String previousHash) {
        this.index = index;
        this.timestamp = Instant.now().toString();
        this.data = data;
        this.previousHash = previousHash;
        this.hash = computeHash();
    }

    public int getIndex() {
        return index;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getData() {
        return data;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    private String computeHash() {
        String content = index + timestamp + data + previousHash;
        return Integer.toHexString(Objects.hash(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return "BlockchainBlock{" +
                "index=" + index +
                ", timestamp='" + timestamp + '\'' +
                ", data='" + data + '\'' +
                ", previousHash='" + previousHash + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
