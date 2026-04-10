# HTTP Server

A lightweight static file server written in Java with no external runtime dependencies. It serves files and browsable directory listings over HTTP from a user-specified root directory.

## Features

- Serves static files with automatic Content-Type detection
- Generates HTML directory listings with links to files and subdirectories
- Supports HTTP GET and HEAD methods (returns 405 for other methods)
- Blocks path traversal attempts outside the document root
- Ships as a Docker container

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for containerized usage)

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
java -jar target/httpserver-1.0-SNAPSHOT.jar 8080 /path/to/files
```

### With Docker

```bash
# Build the image
docker build -t httpserver .

# Run the container, mounting a local directory as the document root
docker run -p 8080:8080 -v /path/to/files:/srv/www httpserver
```

The container defaults to port 8080 and serves from `/srv/www`. Mount your content there with `-v`.

## Running Tests

```bash
mvn test
```

## Project Structure

```
src/main/java/com/adobe/interview/httpserver/
  HttpServer.java          - Entry point; binds the port and accepts connections
  HttpRequest.java         - Immutable model for a parsed HTTP request
  HttpRequestParser.java   - Parses an InputStream into an HttpRequest
  HttpResponse.java        - Response model with writeTo(OutputStream)
  StaticFileHandler.java   - Resolves paths, serves files and directory listings
```
