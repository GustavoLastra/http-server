package com.adobe.interview.httpserver;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;
import com.adobe.interview.httpserver.http.HttpRequestParser;
import com.adobe.interview.httpserver.staticfiles.StaticFileController;
import com.adobe.interview.httpserver.staticfiles.StaticFileService;
import com.adobe.interview.httpserver.staticfiles.DirectoryListingService;
import com.adobe.interview.httpserver.staticfiles.MimeTypeDetector;
import com.adobe.interview.httpserver.staticfiles.CacheUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private Thread serverThread;

    @BeforeEach
    void setUpFiles() throws IOException {
        Files.writeString(tempDir.resolve("index.html"), "<html><body>Welcome</body></html>");
        Files.writeString(tempDir.resolve("readme.txt"), "This is a readme.");
        Path sub = tempDir.resolve("docs");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve("guide.txt"), "Guide content here.");
    }

    private void startServer(int port) throws Exception {
        server = new HttpServer(port, tempDir);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                if (server.isRunning()) {
                    throw new RuntimeException(e);
                }
            }
        });
        serverThread.start();

        for (int i = 0; i < 50; i++) {
            if (server.getLocalPort() != port || server.isRunning()) {
                break;
            }
            Thread.sleep(20);
        }
        Thread.sleep(50);
    }

    /**
     * Reads a single HTTP response (status + headers + body) from the reader
     * without waiting for the connection to close.
     */
    private static ParsedResponse readOneResponse(BufferedReader reader) throws IOException {
        String statusLine = reader.readLine();
        if (statusLine == null) {
            return null;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colon = headerLine.indexOf(':');
            if (colon != -1) {
                headers.put(headerLine.substring(0, colon).trim(), headerLine.substring(colon + 1).trim());
            }
        }

        String body = "";
        String cl = headers.get("Content-Length");
        if (cl != null) {
            int len = Integer.parseInt(cl);
            if (len > 0) {
                char[] buf = new char[len];
                int total = 0;
                while (total < len) {
                    int read = reader.read(buf, total, len - total);
                    if (read == -1) break;
                    total += read;
                }
                body = new String(buf, 0, total);
            }
        }

        return new ParsedResponse(statusLine, headers, body);
    }

    record ParsedResponse(String statusLine, Map<String, String> headers, String body) {}

    /**
     * Sends a request that asks for Connection: close (so the server closes the socket),
     * reads the full response, and returns it as a string.
     */
    private String sendRequest(String rawRequest) throws Exception {
        int actualPort = server.getLocalPort();
        try (Socket socket = new Socket("localhost", actualPort)) {
            OutputStream out = socket.getOutputStream();
            out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    @Test
    void defaultPortIs8080() {
        HttpServer s = new HttpServer(8080, tempDir);
        assertEquals(8080, s.getPort());
    }

    @Test
    void customPortIsAccepted() {
        HttpServer s = new HttpServer(9090, tempDir);
        assertEquals(9090, s.getPort());
    }

    @Test
    void serverServesFileContent() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /readme.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Content-Type: text/plain"));
        assertTrue(response.contains("This is a readme."));
    }

    @Test
    void serverServesHtmlFile() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /index.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Content-Type: text/html"));
        assertTrue(response.contains("Welcome"));
    }

    @Test
    void serverReturns404ForMissingFile() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /nope.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 404 Not Found"));
    }

    @Test
    void serverReturnsDirectoryListing() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200 OK"));
        assertTrue(response.contains("index.html"));
        assertTrue(response.contains("readme.txt"));
        assertTrue(response.contains("docs/"));
    }

    @Test
    void serverServesSubdirectoryListing() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /docs/ HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200 OK"));
        assertTrue(response.contains("guide.txt"));
        assertTrue(response.contains("../"));
    }

    @Test
    void serverServesNestedFile() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /docs/guide.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Guide content here."));
    }

    @Test
    void headRequestReturnsHeadersButNoBody() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            String request = "HEAD /readme.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String statusLine = reader.readLine();
            assertTrue(statusLine.startsWith("HTTP/1.1 200"));

            String headerLine;
            int contentLength = -1;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                }
            }
            assertTrue(contentLength > 0, "HEAD should include Content-Length");
        }
    }

    @Test
    void serverStopsCleanly() throws Exception {
        startServer(0);
        assertTrue(server.isRunning());

        server.stop();
        serverThread.join(2000);

        assertFalse(server.isRunning());
    }

    @Test
    void responseIncludesContentLength() throws Exception {
        startServer(0);
        String response = sendRequest(
                "GET /readme.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertTrue(response.contains("Content-Length:"));
    }

    @Test
    void postReturns405ThroughServer() throws Exception {
        startServer(0);
        String response = sendRequest(
                "POST /readme.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Length: 3\r\n\r\nabc");

        assertTrue(response.startsWith("HTTP/1.1 405"));
    }

    // --- Keep-alive tests ---

    @Test
    void http11KeepAliveByDefault() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            out.write("GET /readme.txt HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp = readOneResponse(reader);
            assertNotNull(resp);
            assertTrue(resp.statusLine().startsWith("HTTP/1.1 200"));
            assertEquals("keep-alive", resp.headers().get("Connection"));
            assertTrue(resp.body().contains("This is a readme."));

            // Send a second request on the same connection
            out.write("GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp2 = readOneResponse(reader);
            assertNotNull(resp2);
            assertTrue(resp2.statusLine().startsWith("HTTP/1.1 200"));
            assertTrue(resp2.body().contains("Welcome"));
        }
    }

    @Test
    void http11ConnectionCloseEndsConnection() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            out.write("GET /readme.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp = readOneResponse(reader);
            assertNotNull(resp);
            assertTrue(resp.statusLine().startsWith("HTTP/1.1 200"));
            assertEquals("close", resp.headers().get("Connection"));

            // Server should have closed the connection; next read returns null
            assertNull(reader.readLine());
        }
    }

    @Test
    void http10ClosedByDefault() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            out.write("GET /readme.txt HTTP/1.0\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp = readOneResponse(reader);
            assertNotNull(resp);
            assertTrue(resp.statusLine().startsWith("HTTP/1.1 200"));
            assertEquals("close", resp.headers().get("Connection"));

            assertNull(reader.readLine());
        }
    }

    @Test
    void http10KeepAliveWhenRequested() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            out.write("GET /readme.txt HTTP/1.0\r\nHost: localhost\r\nConnection: keep-alive\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp = readOneResponse(reader);
            assertNotNull(resp);
            assertTrue(resp.statusLine().startsWith("HTTP/1.1 200"));
            assertEquals("keep-alive", resp.headers().get("Connection"));

            // Second request on same connection
            out.write("GET /index.html HTTP/1.0\r\nHost: localhost\r\nConnection: keep-alive\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            ParsedResponse resp2 = readOneResponse(reader);
            assertNotNull(resp2);
            assertTrue(resp2.statusLine().startsWith("HTTP/1.1 200"));
            assertTrue(resp2.body().contains("Welcome"));
        }
    }

    @Test
    void multipleRequestsOnKeepAliveConnection() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String[] paths = {"/readme.txt", "/index.html", "/docs/guide.txt"};
            for (String path : paths) {
                out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();

                ParsedResponse resp = readOneResponse(reader);
                assertNotNull(resp, "Response for " + path + " should not be null");
                assertTrue(resp.statusLine().startsWith("HTTP/1.1 200"), "200 for " + path);
            }
        }
    }

    @Test
    void keepAliveConnectionClosedByLastRequestWithClose() throws Exception {
        startServer(0);
        int actualPort = server.getLocalPort();

        try (Socket socket = new Socket("localhost", actualPort)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // First request: keep-alive (default for HTTP/1.1)
            out.write("GET /readme.txt HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            ParsedResponse resp1 = readOneResponse(reader);
            assertNotNull(resp1);
            assertEquals("keep-alive", resp1.headers().get("Connection"));

            // Second request: close
            out.write("GET /index.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            ParsedResponse resp2 = readOneResponse(reader);
            assertNotNull(resp2);
            assertEquals("close", resp2.headers().get("Connection"));

            assertNull(reader.readLine());
        }
    }
}
