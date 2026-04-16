# HTTP Server

A lightweight static file server written in Java with no external runtime dependencies. It serves files and browsable directory listings over HTTP from a user-specified root directory.

## Features

- Serves static files with automatic Content-Type detection
- Generates HTML directory listings with links to files and subdirectories
- Supports HTTP GET and HEAD methods (returns 405 for other methods)
- Blocks path traversal attempts outside the document root
- Ships as a Docker container

## Prerequisites

- [mise](https://mise.jdx.dev/) (recommended for toolchain management)
- Docker (for containerized usage)

### Toolchain setup with mise

This project includes a `.mise.toml` that pins Java and Maven versions. With mise installed:

```bash
mise install
```

This will install Java 21 (Temurin) and Maven 3.9 automatically. Without mise, install them manually:

- Java 21
- Maven 3.9+

## Command-Line Parameters

```
java -jar httpserver.jar [port] [documentRoot]
```

| Parameter      | Position | Default | Description                                  |
|----------------|----------|---------|----------------------------------------------|
| `port`         | 1st      | `8080`  | TCP port the server listens on               |
| `documentRoot` | 2nd      | `.`     | Path to the directory whose contents are served |

Both parameters are optional and positional. Examples:

```bash
# Defaults: port 8080, serving current directory
java -jar httpserver.jar

# Custom port, serving current directory
java -jar httpserver.jar 3000

# Custom port and document root
java -jar httpserver.jar 3000 /var/www
```

## Build and Run

### With Maven

```bash
# Compile and run tests
mvn clean package

# Start the server
java -jar target/httpserver-1.0-SNAPSHOT.jar 8080 testfiles
```

### With Docker

```bash
# Build the image
docker build -t httpserver .

# Run the container, mounting a local directory as the document root
docker run -p 8080:8080 -v $(pwd)/testfiles:/srv/www httpserver
```

The container defaults to port 8080 and serves from `/srv/www`. Mount your content there with `-v`.

## Running Tests

```bash
mvn test
```

## Project Structure

```
src/main/java/com/adobe/interview/httpserver/
  HttpServer.java                        - Entry point; binds the port, accepts connections, manages keep-alive loop
  HttpRouter.java                        - Routes requests to the appropriate handler
  http/
    HttpRequest.java                     - Immutable model for a parsed HTTP request
    HttpRequestParser.java               - Parses an InputStream into an HttpRequest
    HttpResponse.java                    - Response model with writeTo(OutputStream)
  staticfiles/
    StaticFileController.java            - Handles static file and directory listing requests
    StaticFileService.java               - Resolves paths and reads file content
    DirectoryListingService.java         - Generates HTML directory listings
    MimeTypeDetector.java                - Detects Content-Type from file extension
    CacheUtil.java                       - ETag and cache validation helpers
```

## Known Issues

Issues detected during code review:

- **Files read for HEAD requests** — `serveFile()` read the full body even for HEAD requests. Fixed in commit `80b5c4c`.
- **No layered architecture** — `StaticFileHandler` was a god class. Initial design lacked class separation across layers (controller / service / utility), reducing reusability and testability from the start.
- **No client socket timeout** — test sockets have no `setSoTimeout`. If the server is busy, the test hangs indefinitely.
- **Keep-alive blocks all other connections** — the server is single-threaded, so one open keep-alive connection blocks the `accept` loop for up to 30 seconds, starving other clients.
- **Test teardown join too short** — after `stop()`, the test only waits 2 seconds for the server thread to die.
- **Timestamp precision** — 304 Not Modified tests compare `Instant.now()` against filesystem timestamps. Some platforms only have 1–2 second precision, so the comparison can flip non-deterministically.

## Bruno Collection

Open Bruno using the `bruno` folder as the collection root (not the repository root and not `bruno/httpserver`).

```bash
open bruno
```

Then in Bruno:
- Open Collection -> select `bruno`
- Choose environment `local`
- Run requests from `bruno/httpserver`

Environment file location:
- `bruno/environments/local.bru`
