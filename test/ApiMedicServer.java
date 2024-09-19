package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ApiMedicServer {

    private static final String AUTH_URL = "https://sandbox-authservice.priaid.ch/login";
    private static final String HEALTH_URL = "https://sandbox-healthservice.priaid.ch";
    private static final String API_USERNAME = "tfm2005@tuta.io";
    private static final String API_PASSWORD = "Rm65Xpa2FHt9c8PQw";
    private static String token = null;

    public static void main(String[] args) throws IOException {
        // Create an HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/diagnose", new SymptomHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // Create a thread pool
        server.start();
        System.out.println("Server started at http://localhost:8080");
    }

    static class SymptomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                JsonObject jsonInput = JsonParser.parseString(requestBody).getAsJsonObject();
                String symptoms = jsonInput.get("symptoms").getAsString();

                // Fetch the token if not already available
                if (token == null) {
                    token = getToken();
                }

                // Get diagnosis based on symptoms
                String diagnosisResponse = getDiagnosis(symptoms);

                // Send response back to frontend
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, diagnosisResponse.getBytes().length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(diagnosisResponse.getBytes());
                outputStream.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }

        // Method to fetch authentication token
        private String getToken() throws IOException {
            OkHttpClient client = new OkHttpClient();

            // Authentication request body
            RequestBody body = new FormBody.Builder()
                    .add("username", API_USERNAME)
                    .add("password", API_PASSWORD)
                    .build();

            // HTTP request to authenticate and retrieve token
            Request request = new Request.Builder()
                    .url(AUTH_URL)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to authenticate: " + response.code());
                }

                String responseBody = response.body().string();
                JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                return jsonObject.get("Token").getAsString();
            }
        }

        // Method to get diagnosis based on symptoms
        private String getDiagnosis(String symptoms) throws IOException {
            OkHttpClient client = new OkHttpClient();

            // Prepare diagnosis request
            HttpUrl url = HttpUrl.parse(HEALTH_URL + "/diagnosis").newBuilder()
                    .addQueryParameter("symptoms", "[" + symptoms + "]") // Example: "[1, 2]"
                    .addQueryParameter("gender", "male")
                    .addQueryParameter("year_of_birth", "1990")
                    .addQueryParameter("token", token)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + token)
                    .build();

            // Execute the request and retrieve diagnosis
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to fetch diagnosis: " + response.code());
                }

                return response.body().string();
            }
        }
    }
}
