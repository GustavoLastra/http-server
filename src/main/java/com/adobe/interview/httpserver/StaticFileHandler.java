package com.adobe.interview.httpserver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class StaticFileHandler {

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry(".html", "text/html"),
            Map.entry(".htm", "text/html"),
            Map.entry(".css", "text/css"),
            Map.entry(".js", "application/javascript"),
            Map.entry(".json", "application/json"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".zip", "application/zip")
    );

    private final Path documentRoot;

    public StaticFileHandler(Path documentRoot) {
        this.documentRoot = documentRoot.toAbsolutePath().normalize();
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

        String etag = generateETag(fileSize, lastModified);
        String lastModifiedStr = formatHttpDate(lastModified);

        String ifMatch = request.getHeaders().get("If-Match");
        if (ifMatch != null && !etagMatches(ifMatch, etag)) {
            HttpResponse response = new HttpResponse(412, "Precondition Failed");
            response.setHeader("ETag", etag);
            response.setBody("412 Precondition Failed");
            return response;
        }

        String ifNoneMatch = request.getHeaders().get("If-None-Match");
        if (ifNoneMatch != null && etagMatches(ifNoneMatch, etag)) {
            return notModified(etag, lastModifiedStr);
        }

        // If-Modified-Since is only evaluated when If-None-Match is absent (RFC 7232 Section 6)
        if (ifNoneMatch == null) {
            String ifModifiedSince = request.getHeaders().get("If-Modified-Since");
            if (ifModifiedSince != null && !modifiedSince(lastModified, ifModifiedSince)) {
                return notModified(etag, lastModifiedStr);
            }
        }

        byte[] content = Files.readAllBytes(file);
        String contentType = detectContentType(file.getFileName().toString());

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

    static String generateETag(long fileSize, Instant lastModified) {
        return "\"" + Long.toHexString(fileSize) + "-" + Long.toHexString(lastModified.toEpochMilli()) + "\"";
    }

    static String formatHttpDate(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private boolean etagMatches(String headerValue, String etag) {
        if (headerValue.trim().equals("*")) {
            return true;
        }
        for (String candidate : headerValue.split(",")) {
            if (candidate.trim().equals(etag)) {
                return true;
            }
        }
        return false;
    }

    private boolean modifiedSince(Instant lastModified, String headerValue) {
        try {
            Instant since = ZonedDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            // HTTP dates have second precision; truncate for comparison
            return lastModified.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                    .isAfter(since.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        } catch (DateTimeParseException e) {
            return true;
        }
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

    private String detectContentType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            String ext = filename.substring(dot).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
