package com.adobe.interview.httpserver;

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

class StaticFileControllerTest {

    @TempDir
    Path tempDir;

    private StaticFileController handler;

    @BeforeEach
    void setUp() throws IOException {
        handler = new StaticFileController(tempDir, new MimeTypeDetector(), new CacheUtil(), new DirectoryListingService());

        Files.writeString(tempDir.resolve("hello.txt"), "Hello, World!");
        Files.writeString(tempDir.resolve("page.html"), "<html><body>Hi</body></html>");
        Files.writeString(tempDir.resolve("data.json"), "{\"key\":\"value\"}");

        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "nested content");
    }

    private HttpRequest get(String path) {
        return new HttpRequest("GET", path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest head(String path) {
        return new HttpRequest("HEAD", path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest request(String method, String path) {
        return new HttpRequest(method, path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest getWithHeaders(String path, Map<String, String> extraHeaders) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "localhost");
        headers.putAll(extraHeaders);
        return new HttpRequest("GET", path, "HTTP/1.1", headers, null);
    }

    @Test
    void getFileReturns200WithCorrectBody() throws IOException {
        HttpResponse response = handler.handle(get("/hello.txt"));

        assertEquals(200, response.getStatusCode());
        assertEquals("Hello, World!", new String(response.getBody()));
        assertEquals("text/plain", response.getHeaders().get("Content-Type"));
    }

    @Test
    void getHtmlFileHasHtmlContentType() throws IOException {
        HttpResponse response = handler.handle(get("/page.html"));

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html", response.getHeaders().get("Content-Type"));
        assertTrue(new String(response.getBody()).contains("<body>Hi</body>"));
    }

    @Test
    void getJsonFileHasJsonContentType() throws IOException {
        HttpResponse response = handler.handle(get("/data.json"));

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void getDirectoryReturnsHtmlListing() throws IOException {
        HttpResponse response = handler.handle(get("/"));

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getHeaders().get("Content-Type").startsWith("text/html"));

        String body = new String(response.getBody());
        assertTrue(body.contains("hello.txt"), "listing should contain hello.txt");
        assertTrue(body.contains("subdir/"), "listing should show subdirectory with trailing /");
        assertTrue(body.contains("page.html"), "listing should contain page.html");
    }

    @Test
    void getSubdirectoryReturnsListing() throws IOException {
        HttpResponse response = handler.handle(get("/subdir/"));

        assertEquals(200, response.getStatusCode());
        String body = new String(response.getBody());
        assertTrue(body.contains("nested.txt"));
        assertTrue(body.contains("../"), "subdirectory listing should include parent link");
    }

    @Test
    void getNestedFileWorks() throws IOException {
        HttpResponse response = handler.handle(get("/subdir/nested.txt"));

        assertEquals(200, response.getStatusCode());
        assertEquals("nested content", new String(response.getBody()));
    }

    @Test
    void getNonexistentPathReturns404() throws IOException {
        HttpResponse response = handler.handle(get("/no-such-file.txt"));

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void headFileReturns200WithEmptyBodyButContentLength() throws IOException {
        HttpResponse response = handler.handle(head("/hello.txt"));

        assertEquals(200, response.getStatusCode());
        assertEquals(0, response.getBody().length);
        String contentLength = response.getHeaders().get("Content-Length");
        assertNotNull(contentLength);
        assertTrue(Integer.parseInt(contentLength) > 0, "Content-Length should reflect actual file size");
    }

    @Test
    void headDirectoryReturns200WithEmptyBody() throws IOException {
        HttpResponse response = handler.handle(head("/"));

        assertEquals(200, response.getStatusCode());
        assertEquals(0, response.getBody().length);
        assertTrue(Integer.parseInt(response.getHeaders().get("Content-Length")) > 0);
    }

    @Test
    void pathTraversalIsBlocked() throws IOException {
        Files.writeString(tempDir.getParent().resolve("secret.txt"), "top secret");

        HttpResponse response = handler.handle(get("/../secret.txt"));

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void postReturns405() throws IOException {
        HttpResponse response = handler.handle(request("POST", "/hello.txt"));

        assertEquals(405, response.getStatusCode());
        assertEquals("GET, HEAD", response.getHeaders().get("Allow"));
    }

    @Test
    void deleteReturns405() throws IOException {
        HttpResponse response = handler.handle(request("DELETE", "/hello.txt"));

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void unknownExtensionGetsOctetStreamType() throws IOException {
        Files.writeString(tempDir.resolve("archive.xyz"), "data");

        HttpResponse response = handler.handle(get("/archive.xyz"));

        assertEquals(200, response.getStatusCode());
        assertEquals("application/octet-stream", response.getHeaders().get("Content-Type"));
    }

    @Test
    void contentLengthMatchesBodyForFile() throws IOException {
        HttpResponse response = handler.handle(get("/hello.txt"));

        int declaredLength = Integer.parseInt(response.getHeaders().get("Content-Length"));
        assertEquals(response.getBody().length, declaredLength);
    }

    // --- ETag and Last-Modified presence ---

    @Test
    void responseIncludesETagAndLastModified() throws IOException {
        HttpResponse response = handler.handle(get("/hello.txt"));

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getHeaders().get("ETag"));
        assertNotNull(response.getHeaders().get("Last-Modified"));
        assertTrue(response.getHeaders().get("ETag").startsWith("\""));
        assertTrue(response.getHeaders().get("ETag").endsWith("\""));
    }

    @Test
    void etagIsConsistentAcrossRequests() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        HttpResponse second = handler.handle(get("/hello.txt"));

        assertEquals(first.getHeaders().get("ETag"), second.getHeaders().get("ETag"));
    }

    // --- If-None-Match ---

    @Test
    void ifNoneMatchWithMatchingEtagReturns304() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        String etag = first.getHeaders().get("ETag");

        HttpResponse conditional = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-None-Match", etag)));

        assertEquals(304, conditional.getStatusCode());
        assertEquals(etag, conditional.getHeaders().get("ETag"));
        assertEquals(0, conditional.getBody().length);
    }

    @Test
    void ifNoneMatchWithNonMatchingEtagReturns200() throws IOException {
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-None-Match", "\"wrong-etag\"")));

        assertEquals(200, response.getStatusCode());
        assertEquals("Hello, World!", new String(response.getBody()));
    }

    @Test
    void ifNoneMatchWithWildcardReturns304() throws IOException {
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-None-Match", "*")));

        assertEquals(304, response.getStatusCode());
    }

    @Test
    void ifNoneMatchWithMultipleEtagsMatchesOne() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        String etag = first.getHeaders().get("ETag");

        HttpResponse conditional = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-None-Match", "\"aaa\", " + etag + ", \"bbb\"")));

        assertEquals(304, conditional.getStatusCode());
    }

    // --- If-Match ---

    @Test
    void ifMatchWithMatchingEtagReturns200() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        String etag = first.getHeaders().get("ETag");

        HttpResponse conditional = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Match", etag)));

        assertEquals(200, conditional.getStatusCode());
    }

    @Test
    void ifMatchWithNonMatchingEtagReturns412() throws IOException {
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Match", "\"stale-etag\"")));

        assertEquals(412, response.getStatusCode());
    }

    @Test
    void ifMatchWithWildcardReturns200() throws IOException {
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Match", "*")));

        assertEquals(200, response.getStatusCode());
    }

    // --- If-Modified-Since ---

    @Test
    void ifModifiedSinceWithFutureDateReturns304() throws IOException {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        String httpDate = new CacheUtil().formatHttpDate(future);

        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Modified-Since", httpDate)));

        assertEquals(304, response.getStatusCode());
    }

    @Test
    void ifModifiedSinceWithPastDateReturns200() throws IOException {
        Instant past = Instant.parse("2000-01-01T00:00:00Z");
        String httpDate = new CacheUtil().formatHttpDate(past);

        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Modified-Since", httpDate)));

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void ifModifiedSinceWithInvalidDateIsIgnored() throws IOException {
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-Modified-Since", "not-a-date")));

        assertEquals(200, response.getStatusCode());
    }

    // --- Precedence: If-None-Match takes priority over If-Modified-Since ---

    @Test
    void ifNoneMatchTakesPriorityOverIfModifiedSince() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        String etag = first.getHeaders().get("ETag");

        // ETag matches → 304, even though If-Modified-Since is in the far past
        HttpResponse conditional = handler.handle(
                getWithHeaders("/hello.txt", Map.of(
                        "If-None-Match", etag,
                        "If-Modified-Since", "Sat, 01 Jan 2000 00:00:00 GMT")));

        assertEquals(304, conditional.getStatusCode());
    }

    @Test
    void nonMatchingIfNoneMatchSkipsIfModifiedSince() throws IOException {
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        String httpDate = new CacheUtil().formatHttpDate(future);

        // ETag doesn't match → 200, regardless of If-Modified-Since
        HttpResponse response = handler.handle(
                getWithHeaders("/hello.txt", Map.of(
                        "If-None-Match", "\"wrong\"",
                        "If-Modified-Since", httpDate)));

        assertEquals(200, response.getStatusCode());
    }

    // --- 304 includes ETag and Last-Modified ---

    @Test
    void notModifiedResponseIncludesETagAndLastModified() throws IOException {
        HttpResponse first = handler.handle(get("/hello.txt"));
        String etag = first.getHeaders().get("ETag");

        HttpResponse conditional = handler.handle(
                getWithHeaders("/hello.txt", Map.of("If-None-Match", etag)));

        assertEquals(304, conditional.getStatusCode());
        assertNotNull(conditional.getHeaders().get("ETag"));
        assertNotNull(conditional.getHeaders().get("Last-Modified"));
    }
}
