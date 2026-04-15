package com.adobe.interview.httpserver.staticfiles;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StaticFileController {

    private final StaticFileService fileService;
    private final DirectoryListingService directoryListingService;

    public StaticFileController(StaticFileService fileService, DirectoryListingService directoryListingService) {
        this.fileService = fileService;
        this.directoryListingService = directoryListingService;
    }

    public HttpResponse handle(HttpRequest request) throws IOException {
        String method = request.getMethod();
        boolean isHeadMethod = method.equals("HEAD");
        boolean isMethodAllowed = isHeadMethod || method.equals("GET");
        if (!isMethodAllowed) {
            HttpResponse response = new HttpResponse(405, "Method Not Allowed");
            response.setHeader("Allow", "GET, HEAD");
            response.setBody("405 Method Not Allowed");
            return response;
        }

        Path safePath = fileService.resolveSafePath(request.getPath());
        if (safePath == null) {
            return fileService.notFound();
        }

        if (Files.isRegularFile(safePath)) {
            return fileService.serveFile(safePath, isHeadMethod, request.getHeaders());
        } else if (Files.isDirectory(safePath)) {
            return directoryListingService.serveDirectory(safePath, request.getPath(), isHeadMethod);
        } else {
            return fileService.notFound();
        }
    }
}
