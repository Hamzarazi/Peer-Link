package p2p.controller;

import p2p.service.FileSharer;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import org.apache.commons.io.IOUtils;


public class FileController {

    private FileSharer fileSharer;
    private HttpServer server;
    private ExecutorService executorService;
    private String uploadDir;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executorService = Executors.newFixedThreadPool(10);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";

        File uploadDirFile = new File(uploadDir);
        if(!uploadDirFile.exists()){
            uploadDirFile.mkdirs();
        }

        server.createContext("upload", new UplaodHandler());
        server.createContext("download", new DownlaodHandler());
        server.createContext("/", new CORSHandler());

        server.setExecutor(executorService);
    }

    public void start(){
        server.start();
        System.out.println("API server started on port: " + server.getAddress().getPort());
    }

    public void stop()
    {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    public class CORSHandler implements HttpHandler{

        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if(exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")){
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response = "Not found";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try(OutputStream os = exchange.getResponseBody()){
                os.write(response.getBytes());
            }
        }
    }

    public class UploadHandler implements HttpHandler{

        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if(!exchange.getRequestMethod().equalsIgnoreCase("POST")){
                String response  = "METHOD NOT ALLOWED";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
                return;
            }
            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");

            if(!contentType.startsWith("multipart/form-data")){
                String response = "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
                return;
            }

            try{
                String boundary = contentType.substring(contentType.indexOf("boundary") + 9);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestBody = baos.toByteArray();
                Multiparser parser = new Multiparser(requestBody, boundary);
            } catch (Exception e) {
                //
            }
        }
    }

}
