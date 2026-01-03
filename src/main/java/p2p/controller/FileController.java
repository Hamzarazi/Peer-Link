package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;
import p2p.service.FileSharer;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final ExecutorService executorService;
    private final String uploadDir;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executorService = Executors.newFixedThreadPool(10);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());

        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API server started on port: " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    private class CORSHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response = "Not found";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static class Multiparser {
        private final byte[] requestData;
        private final String boundary;

        public Multiparser(byte[] requestData, String boundary) {
            this.requestData = requestData;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                //FileName
                String stringData = new String(requestData);
                String filenameMarker = "filename=\"";

                int filenameStart = stringData.indexOf(filenameMarker);
                if (filenameStart == -1) {
                    return null;
                }
                filenameStart += filenameMarker.length();
                int filenameEnd = stringData.indexOf("\"", filenameStart);
                String fileName = stringData.substring(filenameStart, filenameEnd);

                //Content-Type
                String contentTypeMarker = "Content-Type: ";
                int ctStart = stringData.indexOf(contentTypeMarker, filenameEnd);
                String contentType = "application/octet-stream"; // default
                if (ctStart != -1) {
                    ctStart += contentTypeMarker.length();
                    int ctEnd = stringData.indexOf("\r\n", ctStart);
                    contentType = stringData.substring(ctStart, ctEnd);
                }

                // String content

                String headerEndMarker = "\r\n\r\n";
                int headerEnd = stringData.indexOf(headerEndMarker);
                if (headerEnd == -1) return null;

                int contentStart = headerEnd + headerEndMarker.length();
                String boundaryString = "\r\n--" + boundary + "--";
                int contentEnd = stringData.indexOf(boundaryString);
                if (contentEnd == -1) {
                    boundaryString = "\r\n--" + boundary;
                    contentEnd = stringData.indexOf(boundaryString);
                }
                if (contentEnd == -1 || contentEnd <= contentStart) return null;

                byte[] fileContent = new byte[contentEnd - contentStart];

                System.arraycopy(requestData, contentStart, fileContent, 0, fileContent.length);

                return new ParseResult(fileName, contentType, fileContent);
            } catch (Exception ex) {
                System.err.println("Error parsing multipart-data: " + ex.getMessage());
                return null;
            }
        }

        public static class ParseResult {
            public final String filename;
            public final String contentType;
            public final byte[] fileContent;

            public ParseResult(String filename, String contentType, byte[] fileContent) {
                this.filename = filename;
                this.contentType = contentType;
                this.fileContent = fileContent;
            }
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = "METHOD NOT ALLOWED";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");

            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                String response = "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            try {
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();
                Multiparser parser = new Multiparser(requestData, boundary);
                Multiparser.ParseResult result = parser.parse();

                if (result == null) {
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                String fileName = result.filename;
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = "unnamed-file";
                }

                String uniqueFileName = UUID.randomUUID().toString() + "_" + new File(fileName).getName();
                String filepath = uploadDir + File.separator + uniqueFileName;

                try (FileOutputStream fos = new FileOutputStream(filepath)) {
                    fos.write(result.fileContent);
                }

                int port = fileSharer.offerFile(filepath);

                new Thread(() -> fileSharer.startFileServer(port)).start();

                String jsonResponse = "{\"port\": " + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                System.err.println("Error occurred while processing file upload");
                String response = "Server Error: " + e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    private class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String response = "METHOD NOT ALLOWED";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            String filePath = exchange.getRequestURI().toString();
            String strPort = filePath.substring(filePath.lastIndexOf("/") + 1);
            try {
                int port = Integer.parseInt(strPort);

                try (Socket socket = new Socket("localhost", port); InputStream socketInput = socket.getInputStream()) {

                    File tempFile = File.createTempFile("download-", ".tmp");
                    String fileName = "downloaded-file";      //default

                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
                        int b;

                        while ((b = socketInput.read()) != -1) {
                            if (b == '\n') break;
                            headerBaos.write(b);
                        }

                        String header = headerBaos.toString().trim();
                        if (header.startsWith("Filename: ")) {
                            fileName = header.substring("Filename: ".length());
                        }

                        while ((bytesRead = socketInput.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    headers.add("Content-Type", "application/octet-stream");

                    exchange.sendResponseHeaders(200, tempFile.length());
                    try (FileInputStream fis = new FileInputStream(tempFile); OutputStream os = exchange.getResponseBody()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    tempFile.delete();

                } catch (Exception e) {
                    System.err.println("Error downloading file from peer: " + e.getMessage());
                    String response = "Error downloading file: " + e.getMessage();
                    headers.add("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(500, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }

            } catch (NumberFormatException e) {
                System.err.println("Port number received is invalid");
                String response = "Bad Request: Invalid format for the entered port";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }

        }
    }
}
