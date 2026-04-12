package com.adobe.interview.httpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryListingServiceTest {

    @TempDir
    Path tempDir;

    private DirectoryListingService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new DirectoryListingService();
        Files.writeString(tempDir.resolve("file.txt"), "content");
        Files.createDirectory(tempDir.resolve("subdir"));
    }

    @Test
    void listReturns200WithHtmlContentType() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", "GET");
        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    @Test
    void listBodyContainsFileNames() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", "GET");
        String body = new String(response.getBody());
        assertTrue(body.contains("file.txt"));
        assertTrue(body.contains("subdir/"));
    }

    @Test
    void listBodyContainsParentLinkForSubdirectory() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/subdir/", "GET");
        String body = new String(response.getBody());
        assertTrue(body.contains("../"));
    }

    @Test
    void listRootDoesNotContainParentLink() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", "GET");
        String body = new String(response.getBody());
        assertFalse(body.contains("../"));
    }

    @Test
    void listNormalizesPathWithoutTrailingSlash() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/subdir", "GET");
        String body = new String(response.getBody());
        assertTrue(body.contains("Index of /subdir/"));
    }

    @Test
    void listHeadReturns200WithNoBody() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", "HEAD");
        assertEquals(200, response.getStatusCode());
        assertEquals(0, response.getBody().length);
        assertNotNull(response.getHeaders().get("Content-Length"));
    }

    @Test
    void listEscapesSpecialCharactersInHtml() throws IOException {
        Files.writeString(tempDir.resolve("a&b.txt"), "x");
        HttpResponse response = service.serveDirectory(tempDir, "/", "GET");
        String body = new String(response.getBody());
        assertTrue(body.contains("a&amp;b.txt"));
    }
}
