package p2p.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public class CORSHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
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
