package com.adobe.interview.httpserver;

import java.io.IOException;
import java.nio.file.Path;

public class HttpRouter {

    private final StaticFileController staticFileController;

    public HttpRouter(Path documentRoot) {
        this.staticFileController = new StaticFileController(documentRoot, new MimeTypeDetector(), new CacheUtil(), new DirectoryListingService());
    }

    public HttpResponse route(HttpRequest request) throws IOException {
        return staticFileController.handle(request);
    }
}
