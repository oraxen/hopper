package md.thomas.hopper.util;

import md.thomas.hopper.DependencyException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Minimal HTTP client using JDK 11+ HttpClient.
 */
public final class HttpClient {
    
    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
        .build();
    
    private static final String USER_AGENT = "Hopper/1.0 (https://github.com/th0rgal/hopper)";
    
    private HttpClient() {}
    
    /**
     * Perform a GET request and return the response body as a string.
     *
     * @param url the URL to request
     * @return the response body
     * @throws DependencyException if the request fails
     */
    @NotNull
    public static String get(@NotNull String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("HTTP request failed: " + url, e);
        }
    }
    
    /**
     * Perform a GET request with custom headers.
     *
     * @param url the URL to request
     * @param headers headers in pairs (key, value, key, value, ...)
     * @return the response body
     */
    @NotNull
    public static String get(@NotNull String url, String... headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET();
            
            for (int i = 0; i < headers.length - 1; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            
            HttpRequest request = builder.build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("HTTP request failed: " + url, e);
        }
    }
    
    /**
     * Download a file from a URL.
     *
     * @param url the URL to download
     * @param target the target path
     * @throws DependencyException if the download fails
     */
    public static void download(@NotNull String url, @NotNull Path target) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
            
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + ": " + url);
            }
            
            // Ensure parent directories exist
            Files.createDirectories(target.getParent());
            
            // Download to temp file first, then move atomically
            Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");
            try (InputStream in = response.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Download failed: " + url, e);
        }
    }
    
    /**
     * Check if a URL is accessible (returns 2xx status).
     *
     * @param url the URL to check
     * @return true if accessible
     */
    public static boolean isAccessible(@NotNull String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
            
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
