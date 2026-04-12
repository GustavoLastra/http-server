package com.adobe.interview.httpserver;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class StaticFileController {

    private final Path documentRoot;
    private final MimeTypeDetector mimeTypeDetector;
    private final CacheUtil cacheUtil;
    private final DirectoryListingService directoryListingService;

    public StaticFileController(Path documentRoot, MimeTypeDetector mimeTypeDetector, CacheUtil cacheUtil, DirectoryListingService directoryListingService) {
        this.documentRoot = documentRoot.toAbsolutePath().normalize();
        this.mimeTypeDetector = mimeTypeDetector;
        this.cacheUtil = cacheUtil;
        this.directoryListingService = directoryListingService;
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
            return directoryListingService.serveDirectory(resolved, decodedPath, method);
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

    private HttpResponse notFound() {
        HttpResponse response = new HttpResponse(404, "Not Found");
        response.setHeader("Content-Type", "text/plain");
        response.setBody("404 Not Found");
        return response;
    }
}
