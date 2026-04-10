package com.adobe.interview.httpserver;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestParserTest {

    private InputStream toStream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSimpleGetRequest() throws IOException {
        String raw = "GET /index.html HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("GET", request.getMethod());
        assertEquals("/index.html", request.getPath());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("localhost", request.getHeaders().get("Host"));
        assertNull(request.getBody());
    }

    @Test
    void parsesGetRequestWithMultipleHeaders() throws IOException {
        String raw = "GET /api/data HTTP/1.1\r\n"
                + "Host: example.com\r\n"
                + "Accept: application/json\r\n"
                + "User-Agent: TestClient/1.0\r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("GET", request.getMethod());
        assertEquals("/api/data", request.getPath());
        assertEquals(3, request.getHeaders().size());
        assertEquals("example.com", request.getHeaders().get("Host"));
        assertEquals("application/json", request.getHeaders().get("Accept"));
        assertEquals("TestClient/1.0", request.getHeaders().get("User-Agent"));
        assertNull(request.getBody());
    }

    @Test
    void parsesPostRequestWithBody() throws IOException {
        String body = "{\"name\":\"test\"}";
        String raw = "POST /api/users HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("POST", request.getMethod());
        assertEquals("/api/users", request.getPath());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("application/json", request.getHeaders().get("Content-Type"));
        assertEquals(body, request.getBody());
    }

    @Test
    void parsesRequestWithNoHeaders() throws IOException {
        String raw = "GET / HTTP/1.0\r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("GET", request.getMethod());
        assertEquals("/", request.getPath());
        assertEquals("HTTP/1.0", request.getVersion());
        assertTrue(request.getHeaders().isEmpty());
        assertNull(request.getBody());
    }

    @Test
    void returnsNullOnEmptyStream() throws IOException {
        assertNull(HttpRequestParser.parse(toStream("")));
    }

    @Test
    void throwsOnMalformedRequestLine() {
        String raw = "INVALID\r\n\r\n";
        assertThrows(IOException.class, () -> HttpRequestParser.parse(toStream(raw)));
    }

    @Test
    void throwsOnMalformedHeader() {
        String raw = "GET / HTTP/1.1\r\n"
                + "BadHeaderNoColon\r\n"
                + "\r\n";
        assertThrows(IOException.class, () -> HttpRequestParser.parse(toStream(raw)));
    }

    @Test
    void headersMapIsUnmodifiable() throws IOException {
        String raw = "GET / HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertThrows(UnsupportedOperationException.class,
                () -> request.getHeaders().put("New", "value"));
    }

    @Test
    void headerValuesAreTrimmed() throws IOException {
        String raw = "GET / HTTP/1.1\r\n"
                + "Host:   localhost   \r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("localhost", request.getHeaders().get("Host"));
    }

    @Test
    void toStringShowsRequestLine() throws IOException {
        String raw = "DELETE /api/items/42 HTTP/1.1\r\n"
                + "\r\n";

        HttpRequest request = HttpRequestParser.parse(toStream(raw));

        assertEquals("DELETE /api/items/42 HTTP/1.1", request.toString());
    }
}
