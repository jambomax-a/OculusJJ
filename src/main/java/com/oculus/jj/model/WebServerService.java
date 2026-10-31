package com.oculus.jj.model;

import com.oculus.jj.config.AppPreferences;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal WADO HTTP service backed by the active local DICOM database. */
public final class WebServerService {
    private static final Logger logger = LoggerFactory.getLogger(WebServerService.class);
    private static final WebServerService INSTANCE = new WebServerService();

    private HttpServer server;
    private ExecutorService executor;

    private WebServerService() {}

    public static WebServerService getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void start() throws IOException {
        startAt(new InetSocketAddress(AppPreferences.getWebServerPort()));
    }

    synchronized int startForTest() throws IOException {
        startAt(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0));
        return server.getAddress().getPort();
    }

    private void startAt(InetSocketAddress address) throws IOException {
        if (server != null) return;
        if (AppPreferences.getBool("webserver.httpsEnabled", false)) {
            throw new IOException("HTTPS is enabled but no TLS listener is configured.");
        }
        HttpServer next = HttpServer.create(address, 0);
        next.createContext("/wado", this::handleWado);
        ExecutorService nextExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "OculusJJ-WADO");
            thread.setDaemon(true);
            return thread;
        });
        next.setExecutor(nextExecutor);
        next.start();
        server = next;
        executor = nextExecutor;
        logger.info("WADO server started on {}", address);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handleWado(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Only GET is supported.");
                return;
            }
            if (!AppPreferences.isWADOEnabled()) {
                sendError(exchange, 404, "WADO is disabled.");
                return;
            }
            if (AppPreferences.getBool("webserver.wadoIdentifiedOnly", true)
                    || AppPreferences.getBool("webserver.requireAuth", false)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"OculusJJ WADO\"");
                sendError(exchange, 401, "WADO authentication is enabled but user authentication is not configured.");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if (!"WADO".equalsIgnoreCase(query.getOrDefault("RequestType", ""))) {
                sendError(exchange, 400, "RequestType=WADO is required.");
                return;
            }
            String studyUID = query.get("StudyUID");
            String seriesUID = query.get("SeriesUID");
            String sopUID = query.get("SOPInstanceUID");
            if (blank(studyUID) || blank(seriesUID) || blank(sopUID)) {
                sendError(exchange, 400, "StudyUID, SeriesUID, and SOPInstanceUID are required.");
                return;
            }

            DicomDatabase database = DicomDatabase.activeLocalDatabase();
            if (database == null) database = DicomDatabase.defaultDatabase();
            DicomImage image = database == null ? null : database.findImageByUIDs(studyUID, seriesUID, sopUID);
            if (image == null || blank(image.completePath())) {
                sendError(exchange, 404, "DICOM instance not found.");
                return;
            }
            Path path = Path.of(image.completePath());
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                sendError(exchange, 404, "DICOM instance is not available locally.");
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/dicom");
            headers.set("Content-Disposition", "inline; filename=\"" + path.getFileName().toString().replace("\"", "") + "\"");
            exchange.sendResponseHeaders(200, Files.size(path));
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(path, out);
            }
        } catch (IllegalArgumentException ex) {
            sendError(exchange, 400, "Malformed WADO request.");
        } catch (Exception ex) {
            logger.warn("WADO request failed", ex);
            sendError(exchange, 500, "WADO request failed.");
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String key = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
