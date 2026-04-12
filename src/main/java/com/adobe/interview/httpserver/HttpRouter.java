package com.adobe.interview.httpserver;

import java.io.IOException;
import java.nio.file.Path;

public class HttpRouter {

    private final StaticFileController staticFileController;

    public HttpRouter(Path documentRoot) {
        MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
        CacheUtil cacheUtil = new CacheUtil();
        StaticFileService fileService = new StaticFileService(documentRoot, mimeTypeDetector, cacheUtil);
        this.staticFileController = new StaticFileController(fileService, new DirectoryListingService());
    }

    public HttpResponse route(HttpRequest request) throws IOException {
        return staticFileController.handle(request);
    }
}
