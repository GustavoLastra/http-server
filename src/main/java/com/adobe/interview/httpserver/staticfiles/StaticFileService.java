package com.adobe.interview.httpserver.staticfiles;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class StaticFileService {

    private final Path documentRoot;
    private final MimeTypeDetector mimeTypeDetector;
    private final CacheUtil cacheUtil;

    public StaticFileService(Path documentRoot, MimeTypeDetector mimeTypeDetector, CacheUtil cacheUtil) {
        this.documentRoot = documentRoot.toAbsolutePath().normalize();
        this.mimeTypeDetector = mimeTypeDetector;
        this.cacheUtil = cacheUtil;
    }

    /**
     * Resolves a URL request path to a filesystem path.
     * Returns null if the path escapes the document root (path traversal prevention).
     */
    public Path resolveSafePath(String requestPath) {
        String decoded = URLDecoder.decode(requestPath, StandardCharsets.UTF_8);
        Path resolved = documentRoot.resolve(decoded.substring(1)).normalize();
        return resolved.startsWith(documentRoot) ? resolved : null;
    }

    public HttpResponse serveFile(Path file, HttpRequest request) throws IOException {
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

    public HttpResponse notFound() {
        HttpResponse response = new HttpResponse(404, "Not Found");
        response.setHeader("Content-Type", "text/plain");
        response.setBody("404 Not Found");
        return response;
    }

    private HttpResponse notModified(String etag, String lastModified) {
        HttpResponse response = new HttpResponse(304, "Not Modified");
        response.setHeader("ETag", etag);
        response.setHeader("Last-Modified", lastModified);
        return response;
    }
}
