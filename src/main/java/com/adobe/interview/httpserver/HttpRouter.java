package com.adobe.interview.httpserver;

import java.io.IOException;
import java.nio.file.Path;

public class HttpRouter {

    private final StaticFileHandler staticFileHandler;

    public HttpRouter(Path documentRoot) {
        this.staticFileHandler = new StaticFileHandler(documentRoot);
    }

    public HttpResponse route(HttpRequest request) throws IOException {
        return staticFileHandler.handle(request);
    }
}
