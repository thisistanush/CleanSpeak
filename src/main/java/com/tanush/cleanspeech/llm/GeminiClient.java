package com.tanush.cleanspeech.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanush.cleanspeech.config.ConfigManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Google Gemini API.
 * Uses the FREE Gemini 2.0 Flash model for text generation.
 */
public class GeminiClient {

    // Gemini API endpoint
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Request timeout in seconds
    private static final int TIMEOUT_SECONDS = 120;

    // HTTP client for making requests
    private final HttpClient httpClient;

    // Jackson mapper for JSON serialization/deserialization
    private final ObjectMapper objectMapper;

    // API key for authentication
    private final String apiKey;

    /**
     * Creates a new GeminiClient using API key from config.
     *
     * @throws IllegalStateException if API key is not found
     */
    public GeminiClient() {
        String key = ConfigManager.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key not found. Set GEMINI_API_KEY in config.properties.");
        }

        this.apiKey = key;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    /**
     * Sends a chat request to Gemini and returns the response.
     *
     * @param systemPrompt The system instruction that sets the AI's behavior
     * @param userPrompt   The user's message/query
     * @return The AI's response text
     * @throws IOException if the API request fails
     */
    public String sendChatRequest(String systemPrompt, String userPrompt) throws IOException {

        // Build request body for Gemini API
        Map<String, Object> requestBody = new HashMap<>();

        // System instruction
        Map<String, Object> systemInstruction = new HashMap<>();
        Map<String, String> systemPart = new HashMap<>();
        systemPart.put("text", systemPrompt);
        systemInstruction.put("parts", List.of(systemPart));
        requestBody.put("system_instruction", systemInstruction);

        // User content
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        Map<String, String> userPart = new HashMap<>();
        userPart.put("text", userPrompt);
        userContent.put("parts", List.of(userPart));
        contents.add(userContent);
        requestBody.put("contents", contents);

        // Generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3);
        generationConfig.put("maxOutputTokens", 8192);
        requestBody.put("generationConfig", generationConfig);

        // Convert to JSON
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // Build HTTP request with API key as query parameter
        String urlWithKey = GEMINI_API_URL + "?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlWithKey))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Send request
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("API request was interrupted", e);
        }

        // Check response status
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error (status " + response.statusCode() + "): " + response.body());
        }

        // Parse response and extract text content
        JsonNode responseJson = objectMapper.readTree(response.body());
        JsonNode candidates = responseJson.get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No response from Gemini API");
        }

        JsonNode content = candidates.get(0).get("content");
        if (content == null) {
            throw new IOException("No content in Gemini response");
        }

        JsonNode parts = content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("No parts in Gemini response");
        }

        String text = parts.get(0).get("text").asText();

        return text;
    }

    /**
     * Checks if the Gemini API is available (API key is set).
     */
    public static boolean isAvailable() {
        return ConfigManager.isApiKeyAvailable();
    }
}
