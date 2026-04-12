package com.adobe.interview.httpserver.http;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseTest {

    @Test
    void writeToProducesCorrectWireFormat() throws IOException {
        HttpResponse response = new HttpResponse(200, "OK");
        response.setHeader("Content-Type", "text/plain");
        response.setBody("Hello");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.writeTo(out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(result.contains("Content-Type: text/plain\r\n"));
        assertTrue(result.contains("Content-Length: 5\r\n"));
        assertTrue(result.endsWith("\r\n\r\nHello"));
    }

    @Test
    void writeToWith404Status() throws IOException {
        HttpResponse response = new HttpResponse(404, "Not Found");
        response.setBody("not found");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.writeTo(out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith("HTTP/1.1 404 Not Found\r\n"));
        assertTrue(result.contains("Content-Length: 9\r\n"));
    }

    @Test
    void writeToWithNoBody() throws IOException {
        HttpResponse response = new HttpResponse(204, "No Content");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.writeTo(out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith("HTTP/1.1 204 No Content\r\n"));
        assertTrue(result.endsWith("\r\n\r\n"));
    }

    @Test
    void clearBodyKeepsContentLength() throws IOException {
        HttpResponse response = new HttpResponse(200, "OK");
        response.setBody("Hello, World!");
        response.clearBodyKeepContentLength();

        assertEquals(0, response.getBody().length);
        assertEquals("13", response.getHeaders().get("Content-Length"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.writeTo(out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("Content-Length: 13\r\n"));
        assertTrue(result.endsWith("\r\n\r\n"), "Body should be empty after clearBodyKeepContentLength");
    }

    @Test
    void setBodyUpdatesContentLength() {
        HttpResponse response = new HttpResponse(200, "OK");
        response.setBody("abc");
        assertEquals("3", response.getHeaders().get("Content-Length"));

        response.setBody("abcdef");
        assertEquals("6", response.getHeaders().get("Content-Length"));
    }
}
