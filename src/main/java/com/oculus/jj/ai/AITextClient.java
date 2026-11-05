package com.oculus.jj.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Sends user-provided text to a local Ollama or OpenAI-compatible endpoint. */
public final class AITextClient {
    public enum Provider {
        OLLAMA,
        OPENAI_COMPATIBLE
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AITextClient() {
    }

    public static String generate(Provider provider, String baseUrl, String model, String apiKey, String prompt)
            throws IOException, InterruptedException {
        if (provider == null || model == null || model.isBlank() || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Provider, model, and prompt are required.");
        }

        URI endpoint = endpoint(provider, baseUrl);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", model.trim());
        if (provider == Provider.OLLAMA) {
            payload.put("stream", false);
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);
        } else {
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
        if (provider == Provider.OPENAI_COMPATIBLE && apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body();
            if (body.length() > 500) body = body.substring(0, 500);
            throw new IOException("AI endpoint returned HTTP " + response.statusCode() + ": " + body);
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode content = provider == Provider.OLLAMA
                ? root.path("message").path("content")
                : root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IOException("AI endpoint response did not contain generated text.");
        }
        return content.asText();
    }

    private static URI endpoint(Provider provider, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI endpoint URL is required.");
        }
        String base = baseUrl.trim().replaceAll("/+\\s*$", "");
        URI baseUri = URI.create(base);
        String scheme = baseUri.getScheme() == null ? "" : baseUri.getScheme().toLowerCase(Locale.ROOT);
        String host = baseUri.getHost();
        boolean loopback = host != null && (host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1") || host.equals("::1"));
        if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
            throw new IllegalArgumentException("Use HTTPS, except for a local loopback endpoint.");
        }
        if (provider == Provider.OPENAI_COMPATIBLE && baseUri.getPath() != null
                && baseUri.getPath().endsWith("/chat/completions")) {
            return baseUri;
        }
        String suffix = provider == Provider.OLLAMA ? "/api/chat" : "/chat/completions";
        return URI.create(base + suffix);
    }
}