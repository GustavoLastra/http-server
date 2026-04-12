package com.adobe.interview.httpserver.staticfiles;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class DirectoryListingService {

    public HttpResponse serveDirectory(Path dir, String requestPath, String method) throws IOException {
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

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
