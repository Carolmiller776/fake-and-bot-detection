package com.example.ratingbackend;

import com.example.ratingbackend.db.DatabaseManager;
import com.example.ratingbackend.service.RatingBackend;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BackendServer {
    private static final int PORT = 8080;
    private static final String FRONTEND_DIR = "frontend";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        Path databasePath = Path.of("rating.db").toAbsolutePath();
        System.out.println("Using SQLite database at: " + databasePath);

        DatabaseManager databaseManager = new DatabaseManager(databasePath.toString());
        RatingBackend backend = new RatingBackend(databaseManager);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/models", exchange -> handleModels(exchange, backend));
        server.createContext("/api/rate", exchange -> handleRate(exchange, backend));
        server.createContext("/api/status", exchange -> handleStatus(exchange, backend));
        server.createContext("/", BackendServer::serveFrontend);
        server.setExecutor(null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server and closing database...");
            server.stop(0);
            try {
                databaseManager.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        System.out.println("Backend server started at http://localhost:" + PORT);
        server.start();
    }

    private static void handleModels(HttpExchange exchange, RatingBackend backend) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        sendJson(exchange, 200, backend.getKnownModels());
    }

    private static void handleRate(HttpExchange exchange, RatingBackend backend) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = readString(exchange.getRequestBody());
        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String userId = request.has("userId") ? request.get("userId").getAsString() : "";
        String model = request.has("model") ? request.get("model").getAsString() : "default-model";
        String review = request.has("review") ? request.get("review").getAsString() : "";
        int rating = request.has("rating") ? request.get("rating").getAsInt() : 0;
        String ipAddress = request.has("ipAddress") ? request.get("ipAddress").getAsString() : getRemoteIp(exchange);

        if (userId.isBlank() || rating < 1 || rating > 5) {
            sendText(exchange, 400, "userId and rating (1-5) are required");
            return;
        }

        try {
            int reviewCount = backend.getReviewCountByIpAndModel(ipAddress, model);
            if (reviewCount >= 3) {
                JsonObject warningResponse = new JsonObject();
                warningResponse.addProperty("status", "warning");
                warningResponse.addProperty("message", "Alert: This device has submitted 3 or more reviews of this model. Possible bot activity detected.");
                warningResponse.addProperty("reviewCount", reviewCount);
                warningResponse.addProperty("model", model);
                warningResponse.addProperty("userId", userId);
                warningResponse.addProperty("rating", rating);
                sendJson(exchange, 200, warningResponse);
                return;
            }
        } catch (Exception e) {
            // Continue anyway if check fails
        }

        backend.rateAccount(userId, model, review, rating, ipAddress);
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("message", "Rating submitted successfully.");
        result.addProperty("userId", userId);
        result.addProperty("model", model);
        result.addProperty("rating", rating);
        sendJson(exchange, 200, result);
    }

    private static void handleStatus(HttpExchange exchange, RatingBackend backend) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        sendJson(exchange, 200, backend.getStatusSummary());
    }

    private static void serveFrontend(HttpExchange exchange) throws IOException {
        URI requestUri = exchange.getRequestURI();
        String path = requestUri.getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }

        Path file = Path.of(FRONTEND_DIR, path.substring(1));
        if (!Files.exists(file) || Files.isDirectory(file)) {
            sendText(exchange, 404, "Not found");
            return;
        }

        String filename = file.toString();
        String mime;
        if (filename.endsWith(".html")) {
            mime = "text/html; charset=UTF-8";
        } else if (filename.endsWith(".css")) {
            mime = "text/css; charset=UTF-8";
        } else if (filename.endsWith(".js")) {
            mime = "application/javascript; charset=UTF-8";
        } else {
            mime = "application/octet-stream";
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String readString(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String getRemoteIp(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
