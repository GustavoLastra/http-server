package com.adobe.interview.httpserver.staticfiles;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

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
        HttpResponse response = service.serveDirectory(tempDir, "/", false);
        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    @Test
    void listBodyContainsFileNames() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", false);
        String body = new String(response.getBody());
        assertTrue(body.contains("file.txt"));
        assertTrue(body.contains("subdir/"));
    }

    @Test
    void listBodyContainsParentLinkForSubdirectory() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/subdir/", false);
        String body = new String(response.getBody());
        assertTrue(body.contains("../"));
    }

    @Test
    void listRootDoesNotContainParentLink() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/", false);
        String body = new String(response.getBody());
        assertFalse(body.contains("../"));
    }

    @Test
    void listNormalizesPathWithoutTrailingSlash() throws IOException {
        HttpResponse response = service.serveDirectory(tempDir, "/subdir", false);
        String body = new String(response.getBody());
        assertTrue(body.contains("Index of /subdir/"));
    }

    @Test
    void listHeadReturnsNoBodyButSetsContentLength() throws IOException {
        HttpResponse get = service.serveDirectory(tempDir, "/", false);
        HttpResponse head = service.serveDirectory(tempDir, "/", true);
        assertEquals(200, head.getStatusCode());
        assertEquals(0, head.getBody().length);
        assertEquals(get.getHeaders().get("Content-Length"), head.getHeaders().get("Content-Length"));
    }

    @Test
    void listEscapesSpecialCharactersInHtml() throws IOException {
        Files.writeString(tempDir.resolve("a&b.txt"), "x");
        HttpResponse response = service.serveDirectory(tempDir, "/", false);
        String body = new String(response.getBody());
        assertTrue(body.contains("a&amp;b.txt"));
    }
}
