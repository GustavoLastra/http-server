package com.adobe.interview.httpserver.staticfiles;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StaticFileServiceTest {

    @TempDir
    Path tempDir;

    private StaticFileService service;
    private final CacheUtil cacheUtil = new CacheUtil();

    @BeforeEach
    void setUp() throws IOException {
        service = new StaticFileService(tempDir, new MimeTypeDetector(), cacheUtil);
        Files.writeString(tempDir.resolve("hello.txt"), "Hello, World!");
        Files.writeString(tempDir.resolve("page.html"), "<html><body>Hi</body></html>");
    }

    private HttpRequest get(String path) {
        return new HttpRequest("GET", path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest head(String path) {
        return new HttpRequest("HEAD", path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest getWithHeaders(String path, Map<String, String> extra) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "localhost");
        headers.putAll(extra);
        return new HttpRequest("GET", path, "HTTP/1.1", headers, null);
    }

    @Test
    void resolveSafePathReturnsPathInsideRoot() {
        Path result = service.resolveSafePath("/hello.txt");
        assertNotNull(result);
        assertTrue(result.startsWith(tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void resolveSafePathBlocksTraversalAttack() {
        Path result = service.resolveSafePath("/../../../etc/passwd");
        assertNull(result, "Path traversal should return null");
    }

    @Test
    void resolveSafePathDecodesUrlEncoding() {
        Path result = service.resolveSafePath("/hel%6Co.txt");
        assertNotNull(result);
        assertEquals(tempDir.toAbsolutePath().normalize().resolve("hello.txt"), result);
    }

    @Test
    void serveFileReturns200WithBody() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        HttpResponse response = service.serveFile(file, get("/hello.txt"));
        assertEquals(200, response.getStatusCode());
        assertArrayEquals("Hello, World!".getBytes(), response.getBody());
    }

    @Test
    void serveFileHeadReturns200WithNoBody() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        HttpResponse response = service.serveFile(file, head("/hello.txt"));
        assertEquals(200, response.getStatusCode());
        assertEquals(0, response.getBody().length);
        assertEquals(String.valueOf("Hello, World!".length()), response.getHeaders().get("Content-Length"));
    }

    @Test
    void serveFileSetsContentTypeForHtml() throws IOException {
        Path file = tempDir.resolve("page.html");
        HttpResponse response = service.serveFile(file, get("/page.html"));
        assertEquals("text/html", response.getHeaders().get("Content-Type"));
    }

    @Test
    void serveFileSetsEtagAndLastModifiedHeaders() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        HttpResponse response = service.serveFile(file, get("/hello.txt"));
        assertNotNull(response.getHeaders().get("ETag"));
        assertNotNull(response.getHeaders().get("Last-Modified"));
    }

    @Test
    void serveFileReturns304WhenEtagMatches() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        String etag = service.serveFile(file, get("/hello.txt")).getHeaders().get("ETag");
        HttpResponse cached = service.serveFile(file, getWithHeaders("/hello.txt", Map.of("If-None-Match", etag)));
        assertEquals(304, cached.getStatusCode());
    }

    @Test
    void serveFileReturns304WhenNotModifiedSince() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        HttpResponse response = service.serveFile(file,
                getWithHeaders("/hello.txt", Map.of("If-Modified-Since", cacheUtil.formatHttpDate(future))));
        assertEquals(304, response.getStatusCode());
    }

    @Test
    void serveFileReturns412WhenIfMatchFails() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        HttpResponse response = service.serveFile(file,
                getWithHeaders("/hello.txt", Map.of("If-Match", "\"nonexistent-etag\"")));
        assertEquals(412, response.getStatusCode());
    }

    @Test
    void notFoundReturns404() {
        HttpResponse response = service.notFound();
        assertEquals(404, response.getStatusCode());
        assertEquals("text/plain", response.getHeaders().get("Content-Type"));
    }
}
