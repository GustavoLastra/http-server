package com.adobe.interview.httpserver;

import com.adobe.interview.httpserver.http.HttpRequest;
import com.adobe.interview.httpserver.http.HttpResponse;
import com.adobe.interview.httpserver.http.HttpRequestParser;
import com.adobe.interview.httpserver.staticfiles.StaticFileController;
import com.adobe.interview.httpserver.staticfiles.StaticFileService;
import com.adobe.interview.httpserver.staticfiles.DirectoryListingService;
import com.adobe.interview.httpserver.staticfiles.MimeTypeDetector;
import com.adobe.interview.httpserver.staticfiles.CacheUtil;

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
