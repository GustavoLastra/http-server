package com.adobe.interview.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequestParser {

    /**
     * Convenience overload that wraps the InputStream in a BufferedReader.
     * Use the BufferedReader overload for keep-alive connections.
     */
    public static HttpRequest parse(InputStream inputStream) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(inputStream)));
    }

    /**
     * Parses a single HTTP request from the reader.
     * Returns null if the connection was closed cleanly (EOF before any data).
     */
    public static HttpRequest parse(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null) {
            return null;
        }
        if (requestLine.isEmpty()) {
            return null;
        }

        String[] requestParts = requestLine.split(" ");
        if (requestParts.length != 3) {
            throw new IOException("Malformed request line: " + requestLine);
        }

        String method = requestParts[0];
        String path = requestParts[1];
        String version = requestParts[2];

        Map<String, String> headers = new LinkedHashMap<>();
        String headerLine;
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex == -1) {
                throw new IOException("Malformed header: " + headerLine);
            }
            String name = headerLine.substring(0, colonIndex).trim();
            String value = headerLine.substring(colonIndex + 1).trim();
            headers.put(name, value);
        }

        String body = null;
        String contentLength = headers.get("Content-Length");
        if (contentLength != null) {
            int length = Integer.parseInt(contentLength);
            char[] bodyChars = new char[length];
            int totalRead = 0;
            while (totalRead < length) {
                int read = reader.read(bodyChars, totalRead, length - totalRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of stream while reading body");
                }
                totalRead += read;
            }
            body = new String(bodyChars);
        }

        return new HttpRequest(method, path, version, headers, body);
    }
}
