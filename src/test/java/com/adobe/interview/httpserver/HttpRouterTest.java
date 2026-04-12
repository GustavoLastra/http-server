package com.adobe.interview.httpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpRouterTest {

    @TempDir
    Path tempDir;

    private HttpRouter router;

    @BeforeEach
    void setUp() throws IOException {
        router = new HttpRouter(tempDir);
        Files.writeString(tempDir.resolve("hello.txt"), "Hello!");
    }

    private HttpRequest get(String path) {
        return new HttpRequest("GET", path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    private HttpRequest request(String method, String path) {
        return new HttpRequest(method, path, "HTTP/1.1", Map.of("Host", "localhost"), null);
    }

    @Test
    void routesGetFileRequest() throws IOException {
        HttpResponse response = router.route(get("/hello.txt"));
        assertEquals(200, response.getStatusCode());
        assertEquals("Hello!", new String(response.getBody()));
    }

    @Test
    void routesDirectoryRequest() throws IOException {
        HttpResponse response = router.route(get("/"));
        assertEquals(200, response.getStatusCode());
        assertTrue(new String(response.getBody()).contains("hello.txt"));
    }

    @Test
    void routesMissingFileToNotFound() throws IOException {
        HttpResponse response = router.route(get("/nope.txt"));
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void routesUnsupportedMethodToMethodNotAllowed() throws IOException {
        HttpResponse response = router.route(request("POST", "/hello.txt"));
        assertEquals(405, response.getStatusCode());
    }
}
