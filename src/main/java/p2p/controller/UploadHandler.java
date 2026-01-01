package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class UploadHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
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
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(exchange.getRequestBody(), baos);
            byte[] requestBody = baos.toByteArray();
            Multiparser parser = new Multiparser(requestBody, boundary);
            Multiparser.parseResult = parser.parse();

        } catch (Exception e) {
            //
        }
    }
}
