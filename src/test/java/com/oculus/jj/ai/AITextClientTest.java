package com.oculus.jj.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class AITextClientTest {
    @Test
    void sendsOllamaChatRequestToLoopback() throws Exception {
        HttpServer server = server(exchange -> {
            assertEquals("/api/chat", exchange.getRequestURI().getPath());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"model\":\"test-model\""));
            assertTrue(body.contains("\"stream\":false"));
            respond(exchange, "{\"message\":{\"content\":\"草稿\"}}");
        });
        try {
            assertEquals("草稿", AITextClient.generate(AITextClient.Provider.OLLAMA,
                    baseUrl(server), "test-model", null, "メモ"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsBearerTokenToOpenAICompatibleEndpoint() throws Exception {
        HttpServer server = server(exchange -> {
            assertEquals("/v1/chat/completions", exchange.getRequestURI().getPath());
            assertEquals("Bearer test-secret", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "{\"choices\":[{\"message\":{\"content\":\"draft\"}}]}");
        });
        try {
            assertEquals("draft", AITextClient.generate(AITextClient.Provider.OPENAI_COMPATIBLE,
                    baseUrl(server) + "/v1", "test-model", "test-secret", "notes"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsNonTlsRemoteEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> AITextClient.generate(
                AITextClient.Provider.OPENAI_COMPATIBLE, "http://example.com/v1", "test-model", "key", "notes"));
    }

    private static HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (AssertionError error) {
                respond(exchange, "{}");
                throw error;
            }
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}