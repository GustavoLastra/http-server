package com.adobe.interview.httpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypeDetectorTest {

    @Test
    void detectsHtml() {
        assertEquals("text/html", new MimeTypeDetector().detect("index.html"));
    }

    @Test
    void detectsHtm() {
        assertEquals("text/html", new MimeTypeDetector().detect("index.htm"));
    }

    @Test
    void detectsCss() {
        assertEquals("text/css", new MimeTypeDetector().detect("style.css"));
    }

    @Test
    void detectsJavaScript() {
        assertEquals("application/javascript", new MimeTypeDetector().detect("app.js"));
    }

    @Test
    void detectsJson() {
        assertEquals("application/json", new MimeTypeDetector().detect("data.json"));
    }

    @Test
    void detectsPlainText() {
        assertEquals("text/plain", new MimeTypeDetector().detect("readme.txt"));
    }

    @Test
    void detectsPng() {
        assertEquals("image/png", new MimeTypeDetector().detect("photo.png"));
    }

    @Test
    void detectsJpeg() {
        assertEquals("image/jpeg", new MimeTypeDetector().detect("photo.jpg"));
    }

    @Test
    void detectsJpegExtension() {
        assertEquals("image/jpeg", new MimeTypeDetector().detect("photo.jpeg"));
    }

    @Test
    void detectsPdf() {
        assertEquals("application/pdf", new MimeTypeDetector().detect("doc.pdf"));
    }

    @Test
    void detectsZip() {
        assertEquals("application/zip", new MimeTypeDetector().detect("archive.zip"));
    }

    @Test
    void unknownExtensionReturnsOctetStream() {
        assertEquals("application/octet-stream", new MimeTypeDetector().detect("file.xyz"));
    }

    @Test
    void noExtensionReturnsOctetStream() {
        assertEquals("application/octet-stream", new MimeTypeDetector().detect("Makefile"));
    }

    @Test
    void extensionIsCaseInsensitive() {
        assertEquals("text/html", new MimeTypeDetector().detect("index.HTML"));
        assertEquals("image/png", new MimeTypeDetector().detect("photo.PNG"));
    }
}
