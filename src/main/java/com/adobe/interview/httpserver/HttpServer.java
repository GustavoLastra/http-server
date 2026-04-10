package com.adobe.interview.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;

public class HttpServer {

    private static final int KEEP_ALIVE_TIMEOUT_MS = 30_000;

    private final int port;
    private final StaticFileHandler fileHandler;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public HttpServer(int port, Path documentRoot) {
        this.port = port;
        this.fileHandler = new StaticFileHandler(documentRoot);
    }

    public int getPort() {
        return port;
    }

    /**
     * Returns the actual port the server is listening on.
     * Useful when the server was started with port 0 (ephemeral port).
     */
    public int getLocalPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("HTTP server listening on port " + serverSocket.getLocalPort());

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleConnection(clientSocket);
            } catch (IOException e) {
                if (!running) {
                    break;
                }
                System.err.println("Error accepting connection: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket clientSocket) {
        try (clientSocket) {
            clientSocket.setSoTimeout(KEEP_ALIVE_TIMEOUT_MS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            boolean keepAlive = true;
            while (keepAlive) {
                HttpRequest request;
                try {
                    request = HttpRequestParser.parse(reader);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (request == null) {
                    break;
                }
                System.out.println("Received: " + request);

                keepAlive = isKeepAlive(request);
                HttpResponse response = fileHandler.handle(request);
                response.setHeader("Connection", keepAlive ? "keep-alive" : "close");
                response.writeTo(out);
            }
        } catch (IOException e) {
            System.err.println("Error handling connection: " + e.getMessage());
        }
    }

    private boolean isKeepAlive(HttpRequest request) {
        String connection = request.getHeaders().get("Connection");
        if (request.getVersion().equals("HTTP/1.1")) {
            return connection == null || !connection.equalsIgnoreCase("close");
        }
        // HTTP/1.0: close by default unless client asks for keep-alive
        return connection != null && connection.equalsIgnoreCase("keep-alive");
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        Path documentRoot = Path.of(".");
        if (args.length > 1) {
            documentRoot = Path.of(args[1]);
        }
        HttpServer server = new HttpServer(port, documentRoot);
        System.out.println("Document root: " + documentRoot.toAbsolutePath().normalize());
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
