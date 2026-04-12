package com.adobe.interview.httpserver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

public class StaticFileController {

    private final Path documentRoot;
    private final MimeTypeDetector mimeTypeDetector;
    private final CacheUtil cacheUtil;

    public StaticFileController(Path documentRoot, MimeTypeDetector mimeTypeDetector, CacheUtil cacheUtil) {
        this.documentRoot = documentRoot.toAbsolutePath().normalize();
        this.mimeTypeDetector = mimeTypeDetector;
        this.cacheUtil = cacheUtil;
    }

    public HttpResponse handle(HttpRequest request) throws IOException {
        String method = request.getMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            HttpResponse response = new HttpResponse(405, "Method Not Allowed");
            response.setHeader("Allow", "GET, HEAD");
            response.setBody("405 Method Not Allowed");
            return response;
        }

        String decodedPath = URLDecoder.decode(request.getPath(), StandardCharsets.UTF_8);
        Path resolved = documentRoot.resolve(decodedPath.substring(1)).normalize();

        if (!resolved.startsWith(documentRoot)) {
            return notFound();
        }

        if (Files.isRegularFile(resolved)) {
            return serveFile(resolved, request);
        } else if (Files.isDirectory(resolved)) {
            return serveDirectory(resolved, decodedPath, method);
        } else {
            return notFound();
        }
    }

    private HttpResponse serveFile(Path file, HttpRequest request) throws IOException {
        String method = request.getMethod();
        long fileSize = Files.size(file);
        FileTime lastModifiedTime = Files.getLastModifiedTime(file);
        Instant lastModified = lastModifiedTime.toInstant();

        String etag = cacheUtil.generateETag(fileSize, lastModified);
        String lastModifiedStr = cacheUtil.formatHttpDate(lastModified);

        String ifMatch = request.getHeaders().get("If-Match");
        if (ifMatch != null && !cacheUtil.etagMatches(ifMatch, etag)) {
            HttpResponse response = new HttpResponse(412, "Precondition Failed");
            response.setHeader("ETag", etag);
            response.setBody("412 Precondition Failed");
            return response;
        }

        String ifNoneMatch = request.getHeaders().get("If-None-Match");
        if (ifNoneMatch != null && cacheUtil.etagMatches(ifNoneMatch, etag)) {
            return notModified(etag, lastModifiedStr);
        }

        // If-Modified-Since is only evaluated when If-None-Match is absent (RFC 7232 Section 6)
        if (ifNoneMatch == null) {
            String ifModifiedSince = request.getHeaders().get("If-Modified-Since");
            if (ifModifiedSince != null && !cacheUtil.isModifiedSince(lastModified, ifModifiedSince)) {
                return notModified(etag, lastModifiedStr);
            }
        }

        byte[] content = Files.readAllBytes(file);
        String contentType = mimeTypeDetector.detect(file.getFileName().toString());

        HttpResponse response = new HttpResponse(200, "OK");
        response.setHeader("Content-Type", contentType);
        response.setHeader("ETag", etag);
        response.setHeader("Last-Modified", lastModifiedStr);
        response.setBody(content);

        if (method.equals("HEAD")) {
            response.clearBodyKeepContentLength();
        }
        return response;
    }

    private HttpResponse notModified(String etag, String lastModified) {
        HttpResponse response = new HttpResponse(304, "Not Modified");
        response.setHeader("ETag", etag);
        response.setHeader("Last-Modified", lastModified);
        return response;
    }

    private HttpResponse serveDirectory(Path dir, String requestPath, String method) throws IOException {
        String normalizedPath = requestPath.endsWith("/") ? requestPath : requestPath + "/";

        List<Path> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted().toList();
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head><title>Index of ")
                .append(escapeHtml(normalizedPath))
                .append("</title></head>\n<body>\n");
        html.append("<h1>Index of ").append(escapeHtml(normalizedPath)).append("</h1>\n");
        html.append("<ul>\n");

        if (!normalizedPath.equals("/")) {
            html.append("<li><a href=\"../\">../</a></li>\n");
        }

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (Files.isDirectory(entry)) {
                html.append("<li><a href=\"").append(escapeHtml(name)).append("/\">")
                        .append(escapeHtml(name)).append("/</a></li>\n");
            } else {
                html.append("<li><a href=\"").append(escapeHtml(name)).append("\">")
                        .append(escapeHtml(name)).append("</a></li>\n");
            }
        }

        html.append("</ul>\n</body>\n</html>\n");

        HttpResponse response = new HttpResponse(200, "OK");
        response.setHeader("Content-Type", "text/html; charset=utf-8");
        response.setBody(html.toString());

        if (method.equals("HEAD")) {
            response.clearBodyKeepContentLength();
        }
        return response;
    }

    private HttpResponse notFound() {
        HttpResponse response = new HttpResponse(404, "Not Found");
        response.setHeader("Content-Type", "text/plain");
        response.setBody("404 Not Found");
        return response;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
