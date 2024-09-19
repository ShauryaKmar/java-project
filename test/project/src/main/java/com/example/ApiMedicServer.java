package com.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;
import org.json.JSONObject;

public class ApiMedicServer {

    private static final String API_KEY = "tfm2005@tuta.io"; // Replace with actual API key
    private static final String SECRET_KEY = "Rm65Xpa2FHt9c8PQw"; // Replace with actual secret key
    private static final String LOGIN_URL = "https://sandbox-authservice.priaid.ch/login";
    private static final String DIAGNOSIS_URL = "https://sandbox-healthservice.priaid.ch/diagnosis"; // Example URL for diagnosis

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/diagnose", new DiagnoseHandler());
        server.setExecutor(null); // Creates a default executor
        server.start();
        System.out.println("Server started on port 8080");
    }

    private static class DiagnoseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String response = getDiagnosisResult(); // Fetch actual diagnosis result
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                // Set content type to application/json
                exchange.getResponseHeaders().add("Content-Type", "application/json");

                // Send response
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }

        private String getDiagnosisResult() {
            String apiUrl = DIAGNOSIS_URL;
            try {
                // Obtain authentication token
                String token = getAuthToken();
                if (token == null) {
                    return "{\"error\":\"Failed to authenticate\"}";
                }

                // Setup connection to the diagnosis API
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Example of setting up request body if required
                String requestBody = "{\"symptoms\":[]}"; // Adjust according to API requirements
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }

                // Get the response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                return responseBuilder.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "{\"error\":\"Failed to get diagnosis result\"}";
            }
        }

        private String getAuthToken() {
            try {
                // Prepare HMAC MD5 hash for authentication
                String uri = LOGIN_URL;
                String apiKey = API_KEY;
                String secretKey = SECRET_KEY;
                byte[] secretBytes = secretKey.getBytes("UTF-8");

                // Calculate HMAC-MD5 hash
                Mac mac = Mac.getInstance("HmacMD5");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacMD5");
                mac.init(secretKeySpec);

                byte[] hashBytes = mac.doFinal(uri.getBytes("UTF-8"));
                String computedHashString = Base64.getEncoder().encodeToString(hashBytes);

                // Setup the connection
                URL url = new URL(LOGIN_URL + "?format=json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey + ":" + computedHashString);
                connection.setRequestProperty("Content-Length", "0"); // Add Content-Length header
                connection.setDoOutput(true);

                // Get the response
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    InputStream responseStream = connection.getInputStream();
                    Scanner s = new Scanner(responseStream).useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";

                    // Parse the response to get the token
                    JSONObject jsonResponse = new JSONObject(response);
                    return jsonResponse.getString("Token");
                } else {
                    System.out.println("Failed to authenticate: " + responseCode);
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
