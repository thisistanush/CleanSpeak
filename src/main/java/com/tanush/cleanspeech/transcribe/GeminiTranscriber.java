package com.tanush.cleanspeech.transcribe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanush.cleanspeech.config.ConfigManager;
import com.tanush.cleanspeech.model.TranscriptWord;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transcribes audio using Google Gemini 2.0 Flash API.
 * This is a FREE, pure Java solution - no paid APIs required.
 * Gemini can process audio files directly and provide word-level timestamps.
 */
public class GeminiTranscriber {

    // Gemini API endpoint
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Request timeout in seconds (longer for audio processing)
    private static final int TIMEOUT_SECONDS = 180;

    // Jackson mapper for JSON parsing
    private final ObjectMapper objectMapper;

    // HTTP client
    private final HttpClient httpClient;

    // API key for authentication
    private final String apiKey;

    /**
     * Creates a new GeminiTranscriber using API key from config.
     */
    public GeminiTranscriber() {
        this.objectMapper = new ObjectMapper();
        this.apiKey = ConfigManager.getApiKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key not found. Set GEMINI_API_KEY in config.properties.");
        }
    }

    /**
     * Transcribes a WAV file and returns a list of words with timestamps.
     *
     * @param wavFile   Path to the WAV file to transcribe
     * @param outputDir Directory where the transcript will be saved
     * @return List of TranscriptWord objects with word-level timestamps
     * @throws IOException if transcription fails
     */
    public List<TranscriptWord> transcribe(Path wavFile, Path outputDir) throws IOException {
        // Validate input file exists
        if (!Files.exists(wavFile)) {
            throw new IOException("WAV file does not exist: " + wavFile);
        }

        // Read and encode audio file as base64
        byte[] audioBytes = Files.readAllBytes(wavFile);
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        // Send to Gemini for transcription
        String transcriptionResponse = callGeminiForTranscription(audioBase64);

        // Parse the response to extract words with timestamps
        List<TranscriptWord> words = parseTranscriptionResponse(transcriptionResponse);

        // Save transcript for reference
        if (outputDir != null) {
            Files.createDirectories(outputDir);
            String baseName = getBaseName(wavFile.getFileName().toString());
            Path transcriptPath = outputDir.resolve(baseName + "_transcript.txt");
            Files.writeString(transcriptPath, transcriptionResponse);
        }

        return words;
    }

    /**
     * Calls Gemini API with audio for transcription.
     */
    private String callGeminiForTranscription(String audioBase64) throws IOException {
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();

        // System instruction for transcription
        Map<String, Object> systemInstruction = new HashMap<>();
        Map<String, String> systemPart = new HashMap<>();
        systemPart.put("text",
                "You are an audio transcription assistant. Transcribe the audio with word-level timestamps. " +
                        "Output ONLY a JSON array with each word and its start/end time in seconds. " +
                        "Format: [{\"word\": \"hello\", \"start\": 0.0, \"end\": 0.5}, ...]. " +
                        "Be precise with timestamps. Output ONLY valid JSON, no other text.");
        systemInstruction.put("parts", List.of(systemPart));
        requestBody.put("system_instruction", systemInstruction);

        // User content with audio
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");

        // Parts: audio + text instruction
        List<Map<String, Object>> parts = new ArrayList<>();

        // Audio part
        Map<String, Object> audioPart = new HashMap<>();
        Map<String, String> inlineData = new HashMap<>();
        inlineData.put("mimeType", "audio/wav");
        inlineData.put("data", audioBase64);
        audioPart.put("inline_data", inlineData);
        parts.add(audioPart);

        // Text instruction
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", "Transcribe this audio with word-level timestamps. Return ONLY a JSON array.");
        parts.add(textPart);

        userContent.put("parts", parts);
        contents.add(userContent);
        requestBody.put("contents", contents);

        // Generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 8192);
        requestBody.put("generationConfig", generationConfig);

        // Convert to JSON
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // Build HTTP request
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
            throw new IOException("Transcription request was interrupted", e);
        }

        // Check response status
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error (status " + response.statusCode() + "): " + response.body());
        }

        // Parse response
        JsonNode responseJson = objectMapper.readTree(response.body());
        JsonNode candidates = responseJson.get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No transcription response from Gemini");
        }

        JsonNode content = candidates.get(0).get("content");
        if (content == null) {
            throw new IOException("No content in Gemini transcription response");
        }

        JsonNode partsNode = content.get("parts");
        if (partsNode == null || partsNode.isEmpty()) {
            throw new IOException("No parts in Gemini transcription response");
        }

        return partsNode.get(0).get("text").asText();
    }

    /**
     * Parses the Gemini transcription response into TranscriptWord objects.
     */
    private List<TranscriptWord> parseTranscriptionResponse(String response) throws IOException {
        List<TranscriptWord> words = new ArrayList<>();

        // Try to extract JSON from response (Gemini might include extra text)
        String jsonStr = extractJson(response);

        if (jsonStr != null && jsonStr.startsWith("[")) {
            // Parse as JSON array
            try {
                JsonNode wordsArray = objectMapper.readTree(jsonStr);
                if (wordsArray.isArray()) {
                    for (JsonNode wordNode : wordsArray) {
                        String word = wordNode.get("word").asText().trim();
                        double start = wordNode.get("start").asDouble();
                        double end = wordNode.get("end").asDouble();

                        if (!word.isEmpty()) {
                            words.add(new TranscriptWord(word, start, end));
                        }
                    }
                }
            } catch (Exception e) {
                // Fall back to text parsing
                words = parseAsPlainText(response);
            }
        } else {
            // Parse as plain text transcript
            words = parseAsPlainText(response);
        }

        return words;
    }

    /**
     * Extracts JSON array from potentially mixed text.
     */
    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    /**
     * Parses plain text transcript into words with estimated timestamps.
     */
    private List<TranscriptWord> parseAsPlainText(String text) {
        List<TranscriptWord> words = new ArrayList<>();

        // Clean up the text
        String cleanText = text.replaceAll("[\\[\\]{}\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Split into words
        String[] wordArray = cleanText.split("\\s+");

        // Estimate timestamps (assume ~0.3 seconds per word)
        double currentTime = 0.0;
        double wordDuration = 0.3;

        for (String word : wordArray) {
            word = word.replaceAll("[^a-zA-Z0-9']", "").trim();
            if (!word.isEmpty() && word.length() < 30) { // Filter out non-words
                words.add(new TranscriptWord(word, currentTime, currentTime + wordDuration));
                currentTime += wordDuration + 0.1; // Small gap between words
            }
        }

        return words;
    }

    /**
     * Checks if the Gemini API is available (API key is set).
     */
    public static boolean isAvailable() {
        return ConfigManager.isApiKeyAvailable();
    }

    /**
     * Extracts the base name from a filename.
     */
    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex);
        }
        return filename;
    }
}
