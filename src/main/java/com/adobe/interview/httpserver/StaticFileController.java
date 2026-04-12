package com.adobe.interview.httpserver;

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
        if (!method.equals("GET") && !method.equals("HEAD")) {
            HttpResponse response = new HttpResponse(405, "Method Not Allowed");
            response.setHeader("Allow", "GET, HEAD");
            response.setBody("405 Method Not Allowed");
            return response;
        }

        Path resolved = fileService.resolveSafePath(request.getPath());
        if (resolved == null) {
            return fileService.notFound();
        }

        if (Files.isRegularFile(resolved)) {
            return fileService.serveFile(resolved, request);
        } else if (Files.isDirectory(resolved)) {
            return directoryListingService.serveDirectory(resolved, request.getPath(), method);
        } else {
            return fileService.notFound();
        }
    }
}
